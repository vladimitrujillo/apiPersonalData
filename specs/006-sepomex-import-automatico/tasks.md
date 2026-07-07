---

description: "Task list for Automatización de la Actualización del Catálogo SEPOMEX"
---

# Tasks: Automatización de la Actualización del Catálogo SEPOMEX

**Input**: Design documents from `/specs/006-sepomex-import-automatico/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Dependencia**: `002-autenticacion-autorizacion` (tabla `usuario`, `@PreAuthorize` ADMIN) ya
está implementado — las tareas de este archivo son ejecutables.

**Tests**: INCLUDED. El Principio III de la constitución
(`.specify/memory/constitution.md`) es Test-First con Suite Siempre Verde y
NON-NEGOTIABLE. El plan exige explícitamente tests para el candado de concurrencia, el
archivo estructuralmente inválido, la fila individual inválida, y la idempotencia visible.

**⚠️ Nota de arquitectura**: `CatalogoImportacionOrchestrator` (candado + validación +
bitácora + evicción de caché) es la pieza de orquestación que **comparten** US1 (job
programado) y US2 (disparo manual) — por eso vive en Foundational, no en ninguna de las
dos historias, preservando la independencia explícita que pide la spec entre US1 y US2
("US2... independiente de US1"). `SepomexImportService.importar(Path)` cambia su firma de
retorno (`int` → `ResumenImportacion`) en Foundational también, ya que ambas historias
dependen de esa extensión — se documenta explícitamente para no confundirlo con una
reescritura de su lógica de upsert (que NO cambia).

**⚠️ "Ya procesado" es por hash de contenido, no por nombre** (research.md §5,
corregido tras `/speckit-analyze`): el catálogo oficial de SEPOMEX se publica
típicamente bajo un nombre fijo, así que cada actualización periódica legítima llegaría
con el mismo nombre y contenido nuevo — matchear por nombre saltaría siempre las
actualizaciones reales después de la primera vez. `archivo_hash` (SHA-256 del
contenido) es la clave real de "ya procesado"; `archivo` (nombre) es solo informativo.

**Organization**: Tareas agrupadas por historia de usuario (spec.md) para permitir
implementación y prueba independiente de cada una, salvo la pieza compartida señalada
arriba.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: Historia de usuario a la que pertenece (US1..US3)
- Cada tarea incluye la ruta de archivo exacta

## Path Conventions

Proyecto único Maven/Spring Boot (ver `plan.md` → Project Structure):

- `src/main/java/mx/personas/api/...`
- `src/main/resources/db/migration/...`
- `src/test/java/mx/personas/api/...`

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: Extender `SepomexImportService`/`CpCatalogoRepository` mínimamente (mismo
método, mismo upsert, distinto reporte), crear la bitácora y el candado de concurrencia, y
la pieza de orquestación que ambas historias P1 comparten.

**⚠️ CRITICAL**: Ninguna historia de usuario puede implementarse hasta completar esta fase

- [X] T001 Migración
      `src/main/resources/db/migration/V6__create_catalogo_importacion.sql`: tabla
      `catalogo_importacion` (columnas y `CHECK` de `data-model.md`: `id` UUID PK,
      `origen` varchar(20) `CHECK IN ('PROGRAMADA','MANUAL')`, `usuario_id` UUID FK →
      `usuario` nullable, `archivo` text (informativo), `archivo_hash` text `NOT NULL`
      (SHA-256 del contenido — clave real de "ya procesado", research.md §5),
      `fecha_inicio` timestamptz `NOT NULL DEFAULT now()`, `duracion_ms` bigint,
      `insertados`/`actualizados`/`sin_cambio`/`rechazados` int `NOT NULL DEFAULT 0`,
      `estado` varchar(30) `CHECK IN ('EXITO','ERROR','RECHAZADA_CONCURRENCIA')`,
      `detalle_error` text nullable); índice `ix_catalogo_importacion_fecha ON
      catalogo_importacion (fecha_inicio DESC)`; índice `ix_catalogo_importacion_hash_estado
      ON catalogo_importacion (archivo_hash, estado)` (soporta la consulta de "ya
      procesado" de T011) (posterior a `V5` de `005`, no se toca `V1`-`V5`)
- [X] T002 [P] Agregar a `src/main/java/mx/personas/api/common/error/ErrorCode.java`:
      `CATALOGO_ARCHIVO_INVALIDO(HttpStatus.BAD_REQUEST)`,
      `CATALOGO_ARCHIVO_DEMASIADO_GRANDE(HttpStatus.BAD_REQUEST)`,
      `CATALOGO_IMPORTACION_EN_CURSO(HttpStatus.CONFLICT)`; nueva
      `src/main/java/mx/personas/api/common/error/CatalogoArchivoInvalidoException.java`
      (mismo patrón que `FormatoInvalidoException`, para la validación estructural
      previa — FR-011)
- [X] T003 [P] Modificar
      `src/main/java/mx/personas/api/common/error/GlobalExceptionHandler.java`: nuevo
      `@ExceptionHandler(MaxUploadSizeExceededException.class)` → 400
      `CATALOGO_ARCHIVO_DEMASIADO_GRANDE` (research.md §4)
- [X] T004 [P] Nuevo record
      `src/main/java/mx/personas/api/codigopostal/importer/ResumenImportacion.java`:
      `record ResumenImportacion(int insertados, int actualizados, int sinCambio, int
      rechazados, List<String> detallesRechazados)` (research.md §1); agregar
      `boolean tuvoCambios()` (`insertados > 0 || actualizados > 0`) para la decisión de
      evicción de caché (research.md §6)
- [X] T005 Modificar
      `src/main/java/mx/personas/api/codigopostal/repository/CpCatalogoRepository.java`:
      `upsert(...)` cambia de `void` a `Optional<Boolean>` — la sentencia nativa agrega
      `WHERE (...) IS DISTINCT FROM (...)` en el `DO UPDATE` y `RETURNING (xmax = 0) AS
      insertado` (research.md §2 — SQL exacto ya provisto); `Optional.empty()` = sin
      cambio, `true` = insertado, `false` = actualizado; la clave de conflicto
      (`codigo_postal, id_asenta_cpcons`) y los campos actualizados NO cambian
- [X] T006 Modificar
      `src/main/java/mx/personas/api/codigopostal/importer/SepomexImportService.java`:
      `importar(Path)` ahora (a) lee el archivo completo una vez para validar encabezado y
      6 columnas por línea, lanzando `CatalogoArchivoInvalidoException` **antes** de
      cualquier `upsert` si falla (FR-011, research.md §1); (b) en una segunda pasada,
      procesa cada línea individualmente — una fila con un campo inválido (p. ej. CP que
      no son 5 dígitos) se cuenta como rechazada (con su detalle) y continúa con la
      siguiente, sin abortar (FR-012); (c) usa el `Optional<Boolean>` de T005 para
      acumular insertados/actualizados/sinCambio; devuelve `ResumenImportacion` en vez de
      `int`
- [X] T007 Modificar
      `src/main/java/mx/personas/api/codigopostal/importer/SepomexImportRunner.java`:
      adaptar al nuevo tipo de retorno de `importar()` (loguear el resumen completo en
      vez de un conteo simple) — sin cambio de comportamiento (arranque con
      `--import-sepomex=<ruta>`)
- [X] T008 [P] Actualizar
      `src/test/java/mx/personas/api/codigopostal/SepomexImportServiceIT.java`
      (existente): sus aserciones actuales esperan que `importar()` devuelva `int` —
      cambiar a `ResumenImportacion` y ajustar (`resumen.insertados() +
      resumen.actualizados()` en vez del conteo total; el segundo test ya verifica
      "actualiza sin duplicar", ajustar a `resumen.actualizados() == 1` en la segunda
      llamada) — regresión, sin cambiar lo que cada test demuestra
- [X] T009 [P] Nuevo test
      `src/test/java/mx/personas/api/codigopostal/importer/SepomexImportServiceValidacionIT.java`
      (Testcontainers): archivo con encabezado incorrecto o con una línea con menos de 6
      columnas → `CatalogoArchivoInvalidoException`, y el catálogo queda exactamente
      igual que antes (ninguna fila del archivo se insertó, FR-011); archivo con
      estructura válida pero una fila con CP que no son 5 dígitos → esa fila se cuenta en
      `rechazados` (con su detalle), el resto de filas válidas del mismo archivo sí se
      importan (FR-012, Edge Cases)
- [X] T010 Nuevo modelo
      `src/main/java/mx/personas/api/codigopostal/model/CatalogoImportacion.java`:
      entidad JPA de `data-model.md` (tabla `catalogo_importacion`, incluido
      `archivoHash`); enums `OrigenImportacion{PROGRAMADA,MANUAL}` y
      `EstadoImportacion{EXITO,ERROR,RECHAZADA_CONCURRENCIA}` mapeados
      `@Enumerated(EnumType.STRING)` (mismo patrón que `PersonaHistorial.TipoOperacion`);
      solo-`INSERT`, sin setters de estado tras construirse (igual que
      `PersonaHistorial`, data-model.md § Inmutabilidad)
- [X] T011 Nuevo
      `src/main/java/mx/personas/api/codigopostal/repository/CatalogoImportacionRepository.java`:
      `boolean existsByArchivoHashAndEstado(String archivoHash, EstadoImportacion
      estado)` (FR-003, research.md §5 — por **hash**, no por nombre; para que el job
      determine "ya procesado" por bitácora); `Page<CatalogoImportacion>
      findAllByOrderByFechaInicioDesc(Pageable pageable)` (US3)
- [X] T012 Nuevo
      `src/main/java/mx/personas/api/codigopostal/repository/AdvisoryLockRepository.java`:
      método nativo `boolean intentarTomarCandado()` sobre
      `pg_try_advisory_xact_lock(:clave)` con una clave numérica constante
      (research.md §3 — alcance de transacción, no de sesión)
- [X] T013 [P] Nuevo test
      `src/test/java/mx/personas/api/codigopostal/repository/AdvisoryLockRepositoryIT.java`
      (Testcontainers): dos transacciones que intentan el candado casi simultáneamente
      (p. ej. dos hilos, cada uno en su propia transacción) → exactamente una obtiene
      `true`, la otra `false`; al terminar (COMMIT/ROLLBACK) la transacción que lo tomó,
      una tercera transacción puede volver a tomarlo (se libera solo, research.md §3)
- [X] T014 Nuevo
      `src/main/java/mx/personas/api/codigopostal/service/CatalogoImportacionOrchestrator.java`:
      método `ejecutar(Path archivo, String nombreArchivo, String archivoHash,
      OrigenImportacion origen, UUID usuarioId)` que (a) intenta el candado (T012); si no
      lo obtiene, registra una fila `RECHAZADA_CONCURRENCIA` en una transacción corta
      separada y — según el llamador — lanza una excepción de negocio nueva (para que el
      controller responda 409) o simplemente retorna (para que el scheduler salte al
      siguiente ciclo, FR-010); (b) si lo obtiene, ejecuta
      `sepomexImportService.importar(archivo)` (T006) dentro de la misma transacción del
      candado, registra la fila de bitácora (`EXITO`/`ERROR` según el resultado, con el
      `archivoHash` recibido, el resumen y duración medida) y, si es `EXITO` y
      `resumen.tuvoCambios()`, evict-ea `@CacheEvict("codigosPostales", allEntries =
      true)` (research.md §6) — **una sola transacción cubre candado + validación +
      upserts + fila de bitácora exitosa** (research.md §3); el cálculo del
      `archivoHash` es responsabilidad del llamador (T019/T027), no del orquestador
- [X] T015 [P] Nuevo test
      `src/test/java/mx/personas/api/codigopostal/service/CatalogoImportacionOrchestratorTest.java`
      (unit, `sepomexImportService`/`catalogoImportacionRepository`/`advisoryLockRepository`/`cacheManager`
      mockeados): candado no disponible → registra `RECHAZADA_CONCURRENCIA`, no llama a
      `sepomexImportService.importar(...)`; éxito con cambios → registra `EXITO` con el
      resumen y el `archivoHash` correctos, y evict-ea el caché; éxito sin cambios
      (`sinCambio` = total) → NO evict-ea (research.md §6); `CatalogoArchivoInvalidoException`
      de `sepomexImportService.importar(...)` → registra `ERROR` con el detalle, NO
      evict-ea (FR-014)

**Checkpoint**: el candado, la bitácora, y la orquestación (candado + validación +
resumen + evicción) existen y están probados. Ninguna historia de usuario tiene aún su
comportamiento observable (job programado, endpoint manual, consulta de bitácora)
implementado.

---

## Phase 2: User Story 1 - Actualización automática y periódica del catálogo (Priority: P1) 🎯 MVP

**Goal**: Un job `@Scheduled` revisa un directorio configurado, calcula el hash de cada
archivo encontrado, importa los que no tengan ya una corrida exitosa con ese hash en la
bitácora vía el orquestador, y los archiva.

**Independent Test**: Colocar un archivo válido y no procesado en el directorio
configurado, disparar el ciclo del job, confirmar que el catálogo se actualiza, el
archivo queda archivado, y la corrida aparece en la bitácora como exitosa.

### Tests for User Story 1 ⚠️

- [X] T016 [P] [US1] Nuevo integration test
      `src/test/java/mx/personas/api/codigopostal/importer/SepomexImportSchedulerIT.java`
      (Testcontainers; invoca el método anotado `@Scheduled` directamente — no espera al
      cron real): archivo válido y nunca antes procesado en el directorio configurado →
      tras invocar el ciclo, el catálogo se actualiza, el archivo queda movido al
      directorio de procesados, y la bitácora tiene una fila `EXITO`/`PROGRAMADA` con su
      `archivoHash` (Acceptance Scenario 1)
- [X] T017 [P] [US1] Extender el mismo IT: **el caso central de research.md §5** — un
      archivo ya con una corrida `EXITO` registrada para su hash, colocado de nuevo en
      el directorio bajo **el mismo nombre pero con contenido actualizado** (simulando
      cómo SEPOMEX republica su catálogo bajo un nombre fijo) → SÍ se reimporta (el hash
      cambió); el mismo archivo, sin cambios de contenido, en un ciclo posterior → NO se
      reimporta (Acceptance Scenario 2); adicionalmente, con el archivo físico borrado
      del directorio de entrada tras su archivado exitoso (simulando que el archivado
      falló pero la bitácora ya lo tiene) → un archivo nuevo con el mismo hash tampoco se
      reimporta (spec Edge Cases — "la bitácora manda, no el archivo")
- [X] T018 [P] [US1] Extender el mismo IT: archivo con estructura inválida en el
      directorio → el catálogo permanece exactamente igual, y la corrida queda en la
      bitácora con estado `ERROR` y detalle del problema (Acceptance Scenario 3)

### Implementation for User Story 1

- [X] T019 [US1] Nuevo
      `src/main/java/mx/personas/api/codigopostal/importer/SepomexImportScheduler.java`:
      `@Scheduled(cron = "${app.sepomex.import-cron:0 0 3 * * MON}")`; lista los archivos
      del directorio `app.sepomex.directorio-entrada`; para cada uno, calcula su hash
      SHA-256 (mismo patrón `MessageDigest` que `JwtService.hashSha256`, sobre los bytes
      del archivo) y verifica
      `catalogoImportacionRepository.existsByArchivoHashAndEstado(hash, EXITO)` (T011,
      research.md §5) — si ya existe, lo salta sin tocarlo; si no, llama a
      `catalogoImportacionOrchestrator.ejecutar(path, nombreArchivo, hash,
      OrigenImportacion.PROGRAMADA, null)` (T014); si la corrida fue exitosa, mueve el
      archivo a `app.sepomex.directorio-procesados`; si el candado no estaba disponible
      (T014 retorna sin ejecutar), el ciclo simplemente salta ese archivo sin error
      (FR-010, "el ciclo programado... se salta")
- [X] T020 [US1] Agregar a `src/main/resources/application.yml`:
      `app.sepomex.import-cron`, `app.sepomex.directorio-entrada`,
      `app.sepomex.directorio-procesados` (variables de entorno, mismo estilo que
      `app.security.jwt-secret`); agregar `@EnableScheduling` (nueva o en una
      `@Configuration` existente de `common/config`)

**Checkpoint**: US1 funcional e independientemente verificable.

---

## Phase 3: User Story 2 - Disparo manual con resultado inmediato (Priority: P1)

**Goal**: Un ADMIN sube un archivo y la importación se ejecuta de inmediato (siempre,
sin verificar "ya procesado" — a diferencia del job programado, el disparo manual
siempre ejecuta lo que el ADMIN pidió), con el resumen en la respuesta.

**Independent Test**: Autenticado como ADMIN, subir un archivo válido al endpoint manual
y confirmar el resumen en la respuesta; subirlo de nuevo sin cambios y confirmar 0
insertados.

### Tests for User Story 2 ⚠️

- [X] T021 [P] [US2] Nuevo `@WebMvcTest`
      `src/test/java/mx/personas/api/codigopostal/CatalogoImportacionControllerTest.java`:
      CAPTURISTA → 403 en `POST .../importaciones` (Acceptance Scenario 3); candado no
      disponible (orchestrator mockeado lanzando la excepción de concurrencia) → 409
      `CATALOGO_IMPORTACION_EN_CURSO`; archivo inválido (orchestrator mockeado lanzando
      `CatalogoArchivoInvalidoException`) → 400 `CATALOGO_ARCHIVO_INVALIDO`
- [X] T022 [P] [US2] Nuevo integration test
      `src/test/java/mx/personas/api/integration/CatalogoImportacionManualIT.java`
      (Testcontainers): ADMIN sube un archivo válido → 200 con el resumen
      (insertados/actualizados/sinCambio/rechazados, Acceptance Scenario 1); subir el
      mismo archivo de nuevo sin cambios → `insertados: 0` (Acceptance Scenario 2,
      SC-002 — el disparo manual siempre ejecuta, la idempotencia visible viene del
      propio upsert, no de un chequeo de "ya procesado"); archivo con una fila
      individual inválida → esa fila aparece en `detallesRechazados`, el resto se
      importa (FR-012)
- [X] T023 [P] [US2] Extender el mismo IT: archivo corrupto (estructura inválida) → 400
      `CATALOGO_ARCHIVO_INVALIDO`, el catálogo no cambia, y la corrida queda en la
      bitácora con estado `ERROR` (Acceptance Scenario 4)
- [X] T024 [P] [US2] Extender el mismo IT: dos subidas casi simultáneas (dos hilos) →
      exactamente una responde 200, la otra 409 `CATALOGO_IMPORTACION_EN_CURSO`, y ambas
      quedan reflejadas en la bitácora (SC-004, Edge Cases)
- [X] T025 [P] [US2] Extender el mismo IT — **FR-013/SC-005**: mientras una importación
      de un archivo grande está en curso en un hilo (bloqueada deliberadamente el tiempo
      suficiente, p. ej. reutilizando el archivo de catálogo completo de `001`), un
      segundo hilo ejecuta `GET /api/codigos-postales/{cp}` sobre un CP ya cacheado
      previamente → responde 200 con normalidad y sin demora perceptible (el `@Cacheable`
      existente sirve desde caché; no se bloquea esperando el candado ni la transacción
      de la importación en curso)

### Implementation for User Story 2

- [X] T026 [US2] Nuevo
      `src/main/java/mx/personas/api/codigopostal/dto/ResumenImportacionDTO.java`: shape
      exacto de `contracts/catalogo-importacion-api.md` (`insertados, actualizados,
      sinCambio, rechazados, detallesRechazados`)
- [X] T027 [US2] Nuevo
      `src/main/java/mx/personas/api/codigopostal/controller/CatalogoImportacionController.java`:
      `POST /api/codigos-postales/importaciones` (`@PreAuthorize("hasRole('ADMIN')")`,
      `MultipartFile`); escribe el archivo subido a un temporal
      (`Files.createTempFile`), calcula su hash SHA-256 (mismo patrón que T019, para que
      la fila de bitácora quede completa aunque este camino no lo use para decidir si
      ejecutar), obtiene el `login`/id del ADMIN autenticado, llama a
      `catalogoImportacionOrchestrator.ejecutar(..., OrigenImportacion.MANUAL,
      usuarioId)` (T014), mapea el `ResumenImportacion` a `ResumenImportacionDTO`
      (research.md §4 — mismo `importar(Path)` que usa el job, sin duplicar el parseo)
- [X] T028 [US2] Agregar a `src/main/resources/application.yml`:
      `spring.servlet.multipart.max-file-size`/`max-request-size` (configurables,
      research.md §4 — sin código propio de validación de tamaño)

**Checkpoint**: US1 y US2 funcionan de forma independiente entre sí.

---

## Phase 4: User Story 3 - Consultar la bitácora de corridas (Priority: P2)

**Goal**: Un ADMIN consulta el historial paginado de corridas (programadas y manuales).

**Independent Test**: Con al menos una corrida ya registrada, consultar la bitácora como
ADMIN y confirmar que aparece con todos sus datos.

### Tests for User Story 3 ⚠️

- [X] T029 [P] [US3] Extender `CatalogoImportacionControllerTest` (T021): CAPTURISTA →
      403 en `GET .../importaciones` (Acceptance Scenario 2)
- [X] T030 [P] [US3] Nuevo integration test
      `src/test/java/mx/personas/api/integration/CatalogoImportacionBitacoraIT.java`:
      con una corrida programada y una manual ya registradas (provocadas directamente
      vía el orchestrator o los endpoints de US1/US2), un ADMIN consulta la bitácora
      paginada → ambas aparecen, cada una con `origen`, `fecha`, `duracionMs`, resumen y
      `estado`; la manual además indica `usuario` (login), la programada tiene
      `usuario: null` (Acceptance Scenario 1)

### Implementation for User Story 3

- [X] T031 [US3] Nuevo
      `src/main/java/mx/personas/api/codigopostal/dto/CorridaImportacionDTO.java` y
      `CorridaImportacionPageResponseDTO.java`: shape exacto de
      `contracts/catalogo-importacion-api.md` (mismo patrón de paginación que
      `PersonaPageResponseDTO`); `usuario` se resuelve a partir de `usuario_id` (login,
      `null` si `origen = PROGRAMADA`); `archivoHash` **no** se expone en el DTO (dato
      interno de deduplicación, no de interés para el operador que solo quiere ver
      `archivo`/resumen/estado)
- [X] T032 [US3] Modificar `CatalogoImportacionController.java`: `GET
      /api/codigos-postales/importaciones` (`@PreAuthorize("hasRole('ADMIN')")`,
      paginado), usa `CatalogoImportacionRepository.findAllByOrderByFechaInicioDesc`
      (T011) y mapea a `CorridaImportacionPageResponseDTO`

**Checkpoint**: Las 3 historias funcionan juntas de punta a punta.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Verificación final de que el feature completo cumple spec y constitución

- [X] T033 Ejecutar manualmente `quickstart.md` de punta a punta contra la aplicación
      levantada localmente (con `002` también funcionando)
- [X] T034 Correr la suite completa (`mvn test` y `mvn verify` para los `*IT.java`) y
      confirmar 100% verde, incluida la suite ya adaptada por features anteriores
      (FR-016, SC-007)
- [X] T035 [P] Auditar el repo (`grep -rn "sepomexImportService.importar" src/`) y
      confirmar que no queda ningún llamador que siga asumiendo el antiguo retorno `int`
      de `importar(...)`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: sin dependencias externas — BLOQUEA a las 3 historias
- **US1 (Phase 2)**: depende de Foundational (usa el orchestrator ya construido);
  independiente de US2
- **US2 (Phase 3)**: depende de Foundational (usa el mismo orchestrator); independiente
  de US1 — ambas historias son paralelas entre sí, tal como pide la spec explícitamente
- **US3 (Phase 4)**: depende de que existan filas en `catalogo_importacion` para tener
  algo que mostrar en su Independent Test, pero su implementación (el endpoint `GET`) no
  depende del código de US1 ni de US2 — solo de datos que cualquiera de las dos produce
  en tiempo de ejecución
- **Polish (Phase 5)**: depende de que US1-US3 estén completas

### Parallel Opportunities

- Dentro de Foundational: T002/T003 (ErrorCode/handler) en paralelo con T004 (record) y
  con T010-T012 (entidad/repositorio/candado) — archivos distintos
- **US1 y US2 pueden implementarse en paralelo** una vez completada Foundational (no
  comparten archivos de implementación, solo el orchestrator ya existente)
- Dentro de cada fase, las tareas marcadas `[P]` (tests sobre archivos distintos, o sin
  dependencia pendiente entre sí) pueden ejecutarse en paralelo

## Implementation Strategy

### MVP First (US1 solamente)

1. Completar Foundational (T001-T015) — candado, bitácora, orquestación probados
2. Completar US1 (T016-T020) — automatización periódica, el objetivo central del feature
3. **DETENERSE y VALIDAR**: confirmar `SepomexImportSchedulerIT` en verde, en particular
   T017 (el caso de republicación bajo el mismo nombre)

### Entrega incremental

1. Foundational → candado + bitácora + orquestador + extensión mínima del importador
2. US1 → job programado (MVP)
3. US2 → disparo manual con resumen inmediato (en paralelo con US1 si hay capacidad)
4. US3 → consulta de bitácora
5. Polish → validación de punta a punta y limpieza final

---

## Phase 6: Convergence

- [X] T036 Nuevo IT `CatalogoImportacionTamanoMaximoIT` (clase separada, límite
      `max-file-size=1KB` acotado a esta clase para no afectar los archivos grandes de
      otros IT) que sube un archivo mayor al límite a `POST
      /api/codigos-postales/importaciones` contra el servidor embebido real y verifica
      400 `CATALOGO_ARCHIVO_DEMASIADO_GRANDE` — un `@WebMvcTest`/MockMvc no sirve aquí:
      MockMvc no ejerce el parseo real de multipart del contenedor servlet, así que
      `MaxUploadSizeExceededException` nunca se dispara ahí (confirmado empíricamente
      durante esta implementación) per plan.md: research §4 / contrato
      `catalogo-importacion-api.md` (missing)
- [X] T037 `CatalogoImportacionManualIT.dosSubidasCasiSimultaneasSoloUnaTieneExitoLaOtraRegresa409YQuedaEnLaBitacora`
      (renombrado): tras el rechazo por concurrencia, consulta `GET
      /api/codigos-postales/importaciones` y confirma que aparece una fila con
      `estado: RECHAZADA_CONCURRENCIA` y el nombre de archivo correcto (identificado
      dinámicamente según cuál de las dos subidas concurrentes recibió el 409) per
      FR-010/SC-004 (partial)

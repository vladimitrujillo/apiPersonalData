---

description: "Task list for Auditoría y Historial de Cambios en Personas"
---

# Tasks: Auditoría y Historial de Cambios en Personas

**Input**: Design documents from `/specs/003-auditoria-personas/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Dependencia bloqueante satisfecha**: `002-autenticacion-autorizacion` ya está
**implementado** (tabla `usuario`, `SecurityContext` poblado por `JwtAuthenticationFilter`,
`@PreAuthorize` habilitado vía `@EnableMethodSecurity` en `SecurityConfig`). Las tareas de
este archivo son ejecutables.

**Nota de diseño (desviación documentada de research.md §1)**: el `JwtAuthenticationFilter`
de `002`, ya implementado, coloca como principal un `String` (el `login`), no un objeto
enriquecido con `usuario.id` (la alternativa preferida en `research.md §1`, que hubiera
evitado una consulta extra a BD). Modificar `002` (ya implementado y con su propia suite en
verde) para enriquecer el principal es un cambio evitable de mayor riesgo. Este plan usa el
**fallback explícitamente documentado y pre-aprobado** en `research.md §1` ("Alternatives
considered"): `SecurityAuditorAware` resuelve `usuario.id` con una consulta a
`UsuarioRepository.findByLogin(...)` a partir del login del principal. No se modifica
ningún archivo de `002`.

**Tests**: INCLUDED. El Principio III de la constitución (`.specify/memory/constitution.md`)
es Test-First con Suite Siempre Verde y NON-NEGOTIABLE.

**Organization**: Tareas agrupadas por historia de usuario (spec.md) para permitir
implementación y prueba independiente de cada una.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: Historia de usuario a la que pertenece (US1..US3)
- Cada tarea incluye la ruta de archivo exacta

## Path Conventions

Proyecto único Maven/Spring Boot (ver `plan.md` → Project Structure):

- `src/main/java/mx/personas/api/...`
- `src/main/resources/db/migration/...`
- `src/test/java/mx/personas/api/...`

**Nota sobre `001-personas-codigos-postales`**: es documentación histórica del sistema base
ya construido (spec/plan/tasks de la feature original). No se modifica ni se re-implementa;
sólo se referencia como contexto de los contratos que este feature extiende.

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: Infraestructura de auditoría/historial que las 3 historias de usuario
necesitan (columnas `creado_por`/`actualizado_por`, `@MappedSuperclass Auditable`,
`AuditorAware`, tabla `persona_historial`, servicio de diff/enmascarado, DTOs ampliados).
Sin dependencias nuevas de Maven (JPA Auditing ya viene en `spring-boot-starter-data-jpa`;
`JSONB` nativo ya viene en Hibernate 6.5.3 — ver research.md).

**⚠️ CRITICAL**: Ninguna historia de usuario puede implementarse hasta completar esta fase

- [X] T001 Migración `src/main/resources/db/migration/V3__add_auditoria_personas.sql`:
      `ALTER TABLE persona ADD COLUMN creado_por UUID NULL REFERENCES usuario (id)`,
      `ALTER TABLE persona ADD COLUMN actualizado_por UUID NULL REFERENCES usuario (id)`,
      mismas 2 columnas en `direccion`, y `CREATE TABLE persona_historial` (id, persona_id,
      usuario_id, operacion, fecha, cambios JSONB) + `CREATE INDEX
      ix_persona_historial_persona_fecha ON persona_historial (persona_id, fecha DESC)`,
      per data-model.md y research.md §7 (posterior a `V2` de `002`, no se toca `V1`/`V2`)
- [X] T002 [P] Agregar a `src/main/java/mx/personas/api/common/error/ErrorCode.java`:
      `PERSONA_YA_ACTIVA(HttpStatus.CONFLICT)` (FR-015, restaurar sobre persona ya activa)
- [X] T003 [P] `Auditable` (`@MappedSuperclass`) en
      `src/main/java/mx/personas/api/common/audit/Auditable.java`: `@CreatedBy UUID
      creadoPor` (nullable), `@CreatedDate OffsetDateTime createdAt` (column `created_at`,
      not null), `@LastModifiedBy UUID actualizadoPor` (nullable), `@LastModifiedDate
      OffsetDateTime updatedAt` (column `updated_at`, not null), con
      `@EntityListeners(AuditingEntityListener.class)` (research.md §2)
- [X] T004 `SecurityAuditorAware implements AuditorAware<UUID>` en
      `src/main/java/mx/personas/api/common/audit/SecurityAuditorAware.java`: lee
      `SecurityContextHolder.getContext().getAuthentication()`; si no hay autenticación (o
      es anónima) regresa `Optional.empty()`; si la hay, resuelve `usuario.id` con
      `UsuarioRepository.findByLogin(authentication.getName())` (research.md §1, fallback
      documentado — ver nota de diseño arriba)
- [X] T005 [P] `JpaAuditingConfig` en
      `src/main/java/mx/personas/api/common/config/JpaAuditingConfig.java`:
      `@Configuration @EnableJpaAuditing(auditorAwareRef = "securityAuditorAware")`
- [X] T006 [P] `MaskingUtil` en
      `src/main/java/mx/personas/api/common/audit/MaskingUtil.java`: método estático
      `enmascarar(String valor)` que conserva los primeros 2 y los últimos 2 caracteres y
      sustituye el resto por `*` (research.md §5; ejemplo teléfono `5512345678` →
      `55******78`)
- [X] T007 Modificar `src/main/java/mx/personas/api/persona/model/Persona.java`: extiende
      `Auditable`; retira sus propios campos/getters `createdAt`/`updatedAt` y la asignación
      manual en el constructor; retira el método `marcarActualizada()` por completo
      (research.md §2)
- [X] T008 Modificar `src/main/java/mx/personas/api/persona/model/Direccion.java`: extiende
      `Auditable`; retira sus propios campos/getters `createdAt`/`updatedAt` y la asignación
      manual en el constructor y en `actualizar(...)` (research.md §2)
- [X] T009 Quitar la llamada a `persona.marcarActualizada()` (ya eliminado en T007) dentro de
      `src/main/java/mx/personas/api/persona/service/PersonaService.java` (`updatedAt` ahora
      se gestiona automáticamente por JPA Auditing en cualquier `UPDATE` administrado)
- [X] T010 `PersonaHistorial` (entidad) con `enum TipoOperacion` anidado (`CREACION`,
      `MODIFICACION`, `ELIMINACION`, `RESTAURACION`) en
      `src/main/java/mx/personas/api/persona/model/PersonaHistorial.java`: `id`,
      `persona` (`@ManyToOne`), `usuarioId` (UUID plano), `operacion`, `fecha`, `cambios`
      (`String`, `@JdbcTypeCode(SqlTypes.JSON)` — JSONB nativo de Hibernate 6.5, research.md
      §6); sin setters para los campos ya persistidos (inmutable, FR-009)
- [X] T011 [P] `PersonaHistorialRepository` en
      `src/main/java/mx/personas/api/persona/repository/PersonaHistorialRepository.java`:
      `Page<PersonaHistorial> findByPersonaOrderByFechaDesc(Persona persona, Pageable
      pageable)` (mismo estilo que `DireccionRepository.findFirstByPersonaOrderByUpdatedAtDesc`)
- [X] T012 [P] DTOs nuevos en `src/main/java/mx/personas/api/persona/dto/`:
      `CampoCambiadoDTO(String campo, String valorAnterior, String valorNuevo)`,
      `HistorialEntradaDTO(OffsetDateTime fecha, String usuario, String operacion,
      List<CampoCambiadoDTO> cambios)`, `HistorialPageResponseDTO(List<HistorialEntradaDTO>
      contenido, int pagina, int tamanoPagina, long totalElementos, int totalPaginas)`
      (mismo shape de paginación que `PersonaPageResponseDTO` — contracts/personas-historial-api.md)
- [X] T013 `HistorialDiffService` en
      `src/main/java/mx/personas/api/persona/service/HistorialDiffService.java`:
      `serializarCreacion(Persona, Direccion)` (todos los campos persistidos, valorAnterior
      null, con `direccion.` como prefijo en los campos de dirección — research.md §6),
      `serializarModificacion(PersonaSnapshot antes, Persona actual, DireccionSnapshot
      antesDireccion, Direccion actualDireccion)` (solo campos que cambiaron, regresa
      `Optional<String>` vacío si no cambió nada), `serializarCambioEstadoActivo(boolean,
      boolean)` (una entrada `{"campo":"activo",...}` para ELIMINACION/RESTAURACION),
      `deserializar(String)` (para `GET .../historial`); aplica `MaskingUtil` a
      `curp`/`rfc`/`telefono` en ambos sentidos (serializar y deserializar ya vienen
      enmascarados desde el guardado — FR-007); usa `ObjectMapper` (ya presente) para
      serializar/deserializar `List<CampoCambiadoDTO>`; incluye los `record`s internos
      `PersonaSnapshot`/`DireccionSnapshot` (snapshot de campos "antes" para el diff)
- [X] T014 Modificar
      `src/main/java/mx/personas/api/persona/dto/PersonaResponseDTO.java`: agregar
      `creadoPor` (String, login), `creadoEn` (OffsetDateTime), `modificadoPor` (String),
      `modificadoEn` (OffsetDateTime), justo antes de `direccion` (contracts/personas-historial-api.md)
- [X] T015 Modificar
      `src/main/java/mx/personas/api/persona/dto/DireccionResponseDTO.java`: agregar los
      mismos 4 campos de auditoría (FR-002)
- [X] T016 Modificar
      `src/main/java/mx/personas/api/persona/mapper/DireccionMapper.java`: convertir a
      `abstract class` con `UsuarioRepository` inyectado (`@Autowired protected`); método
      `resolverLogin(UUID)` protegido; `@Mapping` con `expression = "java(...)"` para
      `creadoPor`/`modificadoPor` y `source` para `creadoEn`/`modificadoEn` (mismo mecanismo
      que `persona`, FR-002/data-model.md)
- [X] T017 Modificar
      `src/main/java/mx/personas/api/persona/mapper/PersonaMapper.java`: convertir a
      `abstract class` (mismo mecanismo que T016) con `UsuarioRepository` inyectado;
      `toEntity(...)` pasa de `default` a método concreto normal (sin cambios de lógica);
      `toResponseDTO(Persona, Direccion)` agrega los 4 campos de auditoría vía
      `expression`/`source` (delega `direccion` a `DireccionMapper` como ya hacía)
- [X] T018 [P] Adaptar
      `src/test/java/mx/personas/api/persona/PersonaControllerCreateTest.java`: agregar los
      4 argumentos nuevos (valores `null`/fijos de prueba) a las construcciones de
      `PersonaResponseDTO`/`DireccionResponseDTO` para que compile con el nuevo shape
- [X] T019 [P] Adaptar
      `src/test/java/mx/personas/api/persona/PersonaControllerGetTest.java`: idem T018
- [X] T020 [P] Adaptar
      `src/test/java/mx/personas/api/persona/PersonaControllerListTest.java`: idem T018
- [X] T021 [P] Adaptar
      `src/test/java/mx/personas/api/persona/PersonaControllerUpdateTest.java`: idem T018

**Checkpoint**: Compila; migración aplicada; `creado_por`/`actualizado_por` se poblarían
automáticamente en cualquier escritura autenticada; aún no hay endpoint de historial ni de
restauración, y `PersonaService` todavía no escribe filas en `persona_historial` (eso es
US1/US2/US3).

---

## Phase 2: User Story 1 - Ver quién y cuándo tocó una persona (Priority: P1) 🎯 MVP

**Goal**: `GET /api/personas/{id}` incluye quién creó/modificó por última vez a la persona
y a su dirección, y cuándo; el listado (`GET /api/personas`) no incluye esos campos.

**Independent Test**: Crear una persona con un usuario, modificarla con otro, y confirmar
por `GET /api/personas/{id}` que ambos aparecen correctamente atribuidos; confirmar que el
listado no expone esos campos.

**Nota**: la mecánica para que estos campos aparezcan ya quedó resuelta en Foundational
(T003-T004, T014-T017: JPA Auditing + mapeo). Esta fase agrega la verificación explícita
(tests) de que el comportamiento observable coincide con FR-001 a FR-004.

### Tests for User Story 1 ⚠️

- [X] T022 [P] [US1] Extender
      `src/test/java/mx/personas/api/persona/PersonaControllerGetTest.java` (depende de
      T019): nuevo test que verifica, con `jsonPath`, que la respuesta de `GET
      /api/personas/{id}` incluye `creadoPor`, `creadoEn`, `modificadoPor`, `modificadoEn`
      tanto en el nivel raíz como en `direccion` (Acceptance Scenarios US1 #1-#2)
- [X] T023 [P] [US1] Extender
      `src/test/java/mx/personas/api/persona/PersonaControllerListTest.java` (depende de
      T020): nuevo test que verifica, con `jsonPath`, que ningún elemento del listado tiene
      la clave `creadoPor` (Acceptance Scenario US1 #3, FR-004)
- [X] T024 [P] [US1] Nuevo integration test `PersonaAuditoriaIT` en
      `src/test/java/mx/personas/api/integration/PersonaAuditoriaIT.java`: ADMIN crea una
      persona → `GET /api/personas/{id}` muestra `creadoPor`=`modificadoPor`=login del ADMIN
      → CAPTURISTA la modifica (p. ej. teléfono) → `GET /api/personas/{id}` muestra
      `modificadoPor`=login del CAPTURISTA, `creadoPor` sin cambiar → `GET /api/personas`
      (listado) no incluye `creadoPor` en ningún elemento

### Implementation for User Story 1

*(Ninguna: ya cubierta por Foundational T003-T004, T014-T017 — esta historia solo se
verifica con tests)*

**Checkpoint**: US1 funcional y probable de forma independiente.

---

## Phase 3: User Story 2 - Consultar el historial completo de cambios (Priority: P1)

**Goal**: `GET /api/personas/{id}/historial` (solo ADMIN) devuelve, paginado y del más
reciente al más antiguo, cada operación (creación/modificación/eliminación/restauración)
con autor, fecha y campos cambiados (CURP/RFC/teléfono enmascarados).

**Independent Test**: Crear con un usuario, modificar con otro, y confirmar como ADMIN que
el historial trae ambas entradas con sus campos cambiados; confirmar que un CAPTURISTA
recibe 403 en la misma ruta.

### Tests for User Story 2 ⚠️

- [X] T025 [P] [US2] Test unitario `HistorialDiffServiceTest` en
      `src/test/java/mx/personas/api/persona/HistorialDiffServiceTest.java`: `CREACION`
      incluye todos los campos con `valorAnterior=null` (incluidos los de `direccion.`);
      `MODIFICACION` incluye solo los campos que cambiaron; `MODIFICACION` sin cambios reales
      regresa `Optional.empty()`; CURP/RFC/teléfono siempre enmascarados en ambos valores;
      `deserializar` reconstruye la lista original (FR-006 a FR-008, research.md §5-6)
- [X] T026 [P] [US2] `@WebMvcTest` `PersonaControllerHistorialTest` en
      `src/test/java/mx/personas/api/persona/PersonaControllerHistorialTest.java`:
      `@WithMockUser(roles="ADMIN")` → 200 con el shape paginado; `@WithMockUser(roles=
      "CAPTURISTA")` → 403 `ACCESO_DENEGADO`; id inexistente → 404 `PERSONA_NO_ENCONTRADA`
      (contracts/personas-historial-api.md, FR-011, FR-012)
- [X] T027 [P] [US2] Nuevo integration test `PersonaHistorialIT` en
      `src/test/java/mx/personas/api/integration/PersonaHistorialIT.java`: crear (ADMIN) →
      modificar teléfono y calle (CAPTURISTA) → eliminar lógicamente (ADMIN) → `GET
      .../historial` como ADMIN trae `[ELIMINACION, MODIFICACION, CREACION]` en ese orden,
      la entrada `MODIFICACION` incluye `telefono` (enmascarado) y `direccion.calle`;
      `GET .../historial` como CAPTURISTA → 403 (Acceptance Scenarios US2 #1-#4)

### Implementation for User Story 2

- [X] T028 [US2] `PersonaService`: inyectar `PersonaHistorialRepository`,
      `HistorialDiffService`, `SecurityAuditorAware`; en `crear(...)`, tras guardar
      persona+dirección, construir y guardar la entrada `CREACION` (usuario_id resuelto vía
      `securityAuditorAware.getCurrentAuditor()`); en `actualizar(...)`, capturar
      `PersonaSnapshot`/`DireccionSnapshot` **antes** de aplicar los cambios, y tras
      aplicarlos (incluida la dirección si vino en el request) construir el diff con
      `HistorialDiffService.serializarModificacion(...)`, guardando la entrada
      `MODIFICACION` solo si el diff no está vacío; en `eliminar(...)`, tras
      `eliminarLogicamente()`, guardar la entrada `ELIMINACION`
      (`serializarCambioEstadoActivo(true, false)`) (FR-005, FR-006, FR-008, FR-010 — todo
      dentro de la misma transacción de clase ya `@Transactional`)
- [X] T029 [US2] `PersonaService.historial(UUID id, Pageable pageable)`: busca la persona
      **sin** filtrar por `activo` (404 `PERSONA_NO_ENCONTRADA` si no existe en absoluto),
      pagina `PersonaHistorialRepository.findByPersonaOrderByFechaDesc(...)`, mapea cada fila
      a `HistorialEntradaDTO` resolviendo el login del autor vía `UsuarioRepository` y
      deserializando `cambios` vía `HistorialDiffService.deserializar(...)` (FR-011)
- [X] T030 [US2] `PersonaController`: `GET /api/personas/{id}/historial`,
      `@PreAuthorize("hasRole('ADMIN')")`, mismos parámetros de paginación (`page`/`size`)
      que `listar(...)` (FR-012, contracts/personas-historial-api.md)

**Checkpoint**: US1+US2 funcionales juntas — historial completo y auditoría básica
verificables de punta a punta.

---

## Phase 4: User Story 3 - Restaurar una persona eliminada lógicamente (Priority: P2)

**Goal**: Un ADMIN puede revertir una eliminación lógica; la persona vuelve a ser
consultable/listable y la restauración queda en el historial.

**Independent Test**: Eliminar lógicamente una persona, restaurarla, y confirmar que vuelve
a aparecer en `GET /api/personas/{id}` y en el listado, y que su historial incluye la
entrada `RESTAURACION`.

### Tests for User Story 3 ⚠️

- [X] T031 [P] [US3] Nuevo `@WebMvcTest` `PersonaControllerRestaurarTest` en
      `src/test/java/mx/personas/api/persona/PersonaControllerRestaurarTest.java`:
      `@WithMockUser(roles="ADMIN")` → 200 con la persona restaurada; id inexistente → 404;
      persona ya activa → 409 `PERSONA_YA_ACTIVA`; correo/CURP ya tomado por otra persona
      activa → 409 `PERSONA_CORREO_DUPLICADO`/`PERSONA_CURP_DUPLICADO`;
      `@WithMockUser(roles="CAPTURISTA")` → 403 `ACCESO_DENEGADO` (contracts, FR-013 a
      FR-015)
- [X] T032 [P] [US3] Nuevo integration test `PersonaRestaurarIT` en
      `src/test/java/mx/personas/api/integration/PersonaRestaurarIT.java`: eliminar (ADMIN)
      → `GET /api/personas/{id}` 404 y ausente del listado → restaurar (ADMIN) → `GET
      /api/personas/{id}` 200 y presente en el listado → `GET .../historial` incluye
      `RESTAURACION`; CAPTURISTA intenta restaurar → 403; escenario de conflicto: eliminar
      persona A, dar de alta persona B con el correo de A, intentar restaurar A → 409
      `PERSONA_CORREO_DUPLICADO` (Acceptance Scenarios US3 #1-#4, Edge Cases)

### Implementation for User Story 3

- [X] T033 [US3] Método `Persona.restaurar()` en
      `src/main/java/mx/personas/api/persona/model/Persona.java`: `this.activo = true;`
      (mismo estilo que `eliminarLogicamente()`, sin manejo manual de `updatedAt` —
      JPA Auditing ya lo hace)
- [X] T034 [US3] `PersonaService.restaurar(UUID id)`: busca la persona sin filtrar por
      `activo` (404 si no existe); si `activo == true` → `409 PERSONA_YA_ACTIVA`; si no,
      valida `existsByCorreoAndActivoTrue`/`existsByCurpAndActivoTrue` (mismos 409 que
      crear/actualizar); marca `activo = true` con `Persona.restaurar()`; guarda la entrada
      de historial `RESTAURACION` (`serializarCambioEstadoActivo(false, true)`); regresa la
      persona mapeada (FR-013 a FR-015, research.md §8)
- [X] T035 [US3] `PersonaController`: `PATCH /api/personas/{id}/restaurar`,
      `@PreAuthorize("hasRole('ADMIN')")` (FR-013)

**Checkpoint**: Las 3 historias de usuario funcionan juntas de punta a punta.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Verificación final de que el feature completo cumple spec y constitución

- [X] T036 [P] Nuevo integration test `PersonaHistorialRollbackIT` en
      `src/test/java/mx/personas/api/integration/PersonaHistorialRollbackIT.java`: usando
      `@MockBean` sobre `HistorialDiffService` (o `PersonaHistorialRepository`) configurado
      para lanzar una excepción al guardar el historial de una modificación, confirma que
      tras el intento fallido los datos de la persona/dirección quedan exactamente como
      antes (sin cambios parciales) al releerlos en una llamada aparte (SC-006, FR-010)
- [X] T037 Ejecutar manualmente `quickstart.md` de punta a punta contra la aplicación
      levantada localmente (con `002` también funcionando) y ajustar el propio
      `quickstart.md` si algún paso no coincide con el comportamiento real implementado
- [X] T038 Correr la suite completa (`mvn test` y `mvn verify` para los `*IT.java`) y
      confirmar 100% verde, incluida la suite ya adaptada por `002` (FR-016, SC-007)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: Depende de que `002` esté implementado (ya lo está) —
  BLOQUEA las 3 historias de usuario
- **US1 (Phase 2)**: Depende solo de Foundational; su comportamiento ya existe al terminar
  Foundational, esta fase solo lo verifica
- **US2 (Phase 3)**: Depende de Foundational (en particular `PersonaHistorial`,
  `PersonaHistorialRepository`, `HistorialDiffService`, T010-T013)
- **US3 (Phase 4)**: Depende de Foundational y reutiliza el mecanismo de historial de US2
  (`HistorialDiffService.serializarCambioEstadoActivo`, ya usado por `eliminar` en T028)
- **Polish (Phase 5)**: Depende de que las 3 historias estén completas

### User Story Dependencies

- **US1 (P1)**: Ninguna dependencia sobre otras historias — su mecánica ya viene de
  Foundational
- **US2 (P1)**: Depende de Foundational; independiente de US1 y US3 en su implementación,
  aunque comparte los mismos datos subyacentes
- **US3 (P2)**: Depende de Foundational; reutiliza el patrón de escritura de historial que
  US2 valida primero (`eliminar`), pero puede implementarse sin que US2 esté "cerrada"
  formalmente

### Within Each User Story

- Tests escritos y en rojo antes de la implementación correspondiente
- Snapshots/diff antes de persistir; historial se persiste en la misma transacción que la
  operación de negocio (nunca en un método/transacción separada)

### Parallel Opportunities

- Dentro de Foundational: T002, T003, T005, T006 en paralelo; T011, T012 en paralelo;
  T018-T021 (adaptación de tests existentes) todas en paralelo entre sí
- Dentro de US1: T022-T024 en paralelo (archivos distintos)
- Dentro de US2: T025-T027 en paralelo
- Dentro de US3: T031-T032 en paralelo

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Fase 1: Foundational (incluye ya la mecánica de US1)
2. Completar Fase 2: US1 (solo tests de verificación)
3. **DETENER y VALIDAR**: probar US1 de forma independiente (quickstart.md §2)

### Incremental Delivery

1. Foundational → auditoría básica ya visible (US1 gratis)
2. + US2 → historial completo consultable por ADMIN
3. + US3 → restauración de personas eliminadas
4. Cada historia agrega valor sin romper a las anteriores (suite siempre verde)

---

## Notes

- [P] tareas = archivos distintos, sin dependencias pendientes
- [Story] etiqueta cada tarea con su historia de usuario para trazabilidad
- `DireccionMapper.java` (T016) se modifica aunque el `Project Structure` de `plan.md` no lo
  liste explícitamente: es la consecuencia natural de que `DireccionResponseDTO` también
  gane campos de auditoría (data-model.md dice "mismo mecanismo" que `persona`) — mantiene a
  `DireccionMapper` como el dueño de la representación completa de `Direccion` en vez de
  dejarlo como código muerto sin uso.
- `001-personas-codigos-postales` es documentación histórica; no se toca.

---

## Phase 6: Convergence

- [X] T039 Agregar un test unitario que instancie directamente `PersonaMapperImpl`/
      `DireccionMapperImpl` (los mappers generados por MapStruct) con un
      `UsuarioRepository` simulado (Mockito), verificando `resolverLogin` (login resuelto
      cuando el UUID existe, `null` cuando el UUID es `null` o no se encuentra), y que
      `toResumenDTO`/`DireccionResumenDTO` nunca exponen `creadoPor`/`creadoEn`/
      `modificadoPor`/`modificadoEn` — el mecanismo real de FR-003/FR-004 (US1) no lo
      ejercita hoy ningún test ejecutable en este entorno (partial)
- [X] T040 En `src/main/java/mx/personas/api/persona/mapper/PersonaMapper.java`, blindar
      las expresiones `@Mapping(target = "creadoPor"/"modificadoPor", expression =
      "java(resolverLogin(persona.get...()))")` contra `persona == null` (el método
      `toResponseDTO(Persona, Direccion)` generado guarda `if (persona == null &&
      direccion == null) return null`, pero esas expresiones llaman a
      `persona.getCreadoPor()` sin condición, produciendo un NPE si `persona` es null y
      `direccion` no — hoy inalcanzable porque ningún llamador pasa `persona` null, pero
      contradice la propia guarda nula del método generado) (contradicts)
- [X] T041 Agregar a
      `src/test/java/mx/personas/api/common/error/GlobalExceptionHandlerTest.java` un test
      unitario directo para `PersonaYaActivaException` → `409 PERSONA_YA_ACTIVA`,
      siguiendo el mismo patrón de un test por excepción ya usado en ese archivo (FR-015)
      (partial)

---

## Phase 7: Convergence

- [X] T042 Agregar `src/test/java/mx/personas/api/persona/PersonaServiceTest.java` (Mockito,
      sin contexto de Spring, mismo patrón que `AuthServiceTest`/`UsuarioServiceTest`)
      cubriendo la orquestación real de `PersonaService`: `crear` (persiste historial
      CREACION con el usuario_id resuelto), `actualizar` (el snapshot "antes" se toma antes
      de mutar; una modificación que solo cambia la dirección también genera una entrada
      MODIFICACION; una modificación sin cambios reales no persiste ninguna entrada),
      `eliminar` (entrada ELIMINACION), `restaurar` (404 si no existe, 409
      `PERSONA_YA_ACTIVA` si ya está activa, 409 `PERSONA_CORREO_DUPLICADO`/
      `PERSONA_CURP_DUPLICADO` si el correo/CURP ya está tomado, entrada RESTAURACION en el
      caso exitoso), y `historial` (404 si el id no existe en absoluto, mapeo correcto de
      página a `HistorialEntradaDTO`) — hoy esta lógica, la más compleja de todo el feature,
      solo la ejercitan `@WebMvcTest` que mockean `PersonaService` por completo y los
      `*IT.java` que requieren Docker (no disponible en este entorno) per FR-005 a FR-015
      (partial)
- [X] T043 Agregar `src/test/java/mx/personas/api/common/audit/SecurityAuditorAwareTest.java`
      (unitario, sin contexto de Spring): con un `UsuarioRepository` simulado y
      `SecurityContextHolder.getContext().setAuthentication(...)` fijado manualmente,
      verificar que un login autenticado y existente resuelve al UUID correcto, y que sin
      autenticación, con autenticación no autenticada, o con un login que no corresponde a
      ningún `usuario`, `getCurrentAuditor()` regresa `Optional.empty()` — el mecanismo que
      resuelve "quién" para cada fila de auditoría/historial no tiene hoy ningún test
      per FR-001/FR-002 (partial)

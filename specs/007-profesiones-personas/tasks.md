---

description: "Task list for Catálogo de Profesiones y Asignación a Personas"
---

# Tasks: Catálogo de Profesiones y Asignación a Personas

**Input**: Design documents from `/specs/007-profesiones-personas/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED. El Principio III de la constitución
(`.specify/memory/constitution.md`) es Test-First con Suite Siempre Verde y
NON-NEGOTIABLE. Se incluyen tests para: unicidad insensible a
mayúsculas/acentos (Testcontainers, `unaccent` no existe en H2), unicidad de
asignación activa (índice parcial), bloqueo por profesión desactivada o
persona eliminada, reasignación tras retiro (episodio nuevo), exclusión del
directorio para personas eliminadas/asignaciones retiradas, y permisos por
rol.

**Organization**: Tareas agrupadas por historia de usuario (spec.md) para
permitir implementación y prueba independiente de cada una.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias
  pendientes)
- **[Story]**: Historia de usuario a la que pertenece (US1..US5)
- Cada tarea incluye la ruta de archivo exacta

## Path Conventions

Proyecto único Maven/Spring Boot (ver `plan.md` → Project Structure):

- `src/main/java/mx/personas/api/...`
- `src/main/resources/db/migration/...`
- `src/test/java/mx/personas/api/...`

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: Migración, entidades, repositorios y errores compartidos por
las 5 historias de usuario.

**⚠️ CRITICAL**: Ninguna historia de usuario puede implementarse hasta
completar esta fase

- [X] T001 Migración `src/main/resources/db/migration/V7__create_profesion_persona_profesion.sql`:
      `CREATE EXTENSION IF NOT EXISTS unaccent` (precaución idempotente,
      research.md §1); tabla `profesion` (`id BIGSERIAL PK`, `nombre
      VARCHAR(80) NOT NULL`, `descripcion TEXT`, `activo BOOLEAN NOT NULL
      DEFAULT true`, columnas de `Auditable`); índice único funcional
      `ux_profesion_nombre_unaccent ON profesion
      (unaccent_immutable(LOWER(nombre)))` reutilizando la función ya creada
      por `V5` (research.md §1-2, NO declarar una función propia); tabla
      `persona_profesion` (`id UUID PK`, `persona_id UUID NOT NULL REFERENCES
      persona(id)`, `profesion_id BIGINT NOT NULL REFERENCES profesion(id)`,
      `fecha_desde DATE NOT NULL DEFAULT CURRENT_DATE`, `cedula VARCHAR(30)`,
      `activo BOOLEAN NOT NULL DEFAULT true`, columnas de `Auditable`);
      índice único parcial `ux_persona_profesion_activa ON persona_profesion
      (persona_id, profesion_id) WHERE activo = true` (research.md §3);
      índices `ix_persona_profesion_persona_id`,
      `ix_persona_profesion_profesion_id`; semilla `INSERT` idempotente de la
      profesión "Mecánico" (data-model.md)
- [X] T002 [P] Agregar a `src/main/java/mx/personas/api/common/error/ErrorCode.java`:
      `PROFESION_NOMBRE_DUPLICADO(HttpStatus.CONFLICT)`,
      `PROFESION_NOMBRE_DESACTIVADA(HttpStatus.CONFLICT)`,
      `PROFESION_DESACTIVADA(HttpStatus.CONFLICT)`,
      `PROFESION_NO_ENCONTRADA(HttpStatus.NOT_FOUND)`,
      `PERSONA_PROFESION_YA_ASIGNADA(HttpStatus.CONFLICT)`,
      `PERSONA_ELIMINADA(HttpStatus.CONFLICT)`,
      `PERSONA_PROFESION_NO_ENCONTRADA(HttpStatus.NOT_FOUND)`,
      `PERSONA_PROFESION_YA_RETIRADA(HttpStatus.CONFLICT)` (contracts/profesiones-api.md)
- [X] T003 [P] **Ajuste durante implementación**: no se creó
      `ProfesionNombreDesactivadaException` — se confirmó que
      `PERSONA_CURP_ELIMINADA` (el precedente citado) en realidad reutiliza
      `DuplicateFieldException` con el id en el mensaje (ver
      `PersonaService.validarCurpDisponible`), sin una clase dedicada. Se
      aplicó el mismo patrón: `PROFESION_NOMBRE_DESACTIVADA` también reutiliza
      `DuplicateFieldException` (FR-005) — sin archivo nuevo
- [X] T004 [P] Nueva `src/main/java/mx/personas/api/common/error/ProfesionDesactivadaException.java`
      (mismo patrón simple que `PersonaYaActivaException` — FR-009)
- [X] T005 [P] **Ajuste durante implementación**: no se creó
      `ProfesionNoEncontradaException` — ya existe
      `RecursoNoEncontradoException(ErrorCode, mensaje)`, genérica para
      cualquier 404; se reutiliza para `PROFESION_NO_ENCONTRADA` sin archivo
      nuevo
- [X] T006 [P] Nueva `src/main/java/mx/personas/api/common/error/PersonaProfesionYaAsignadaException.java`
      (FR-013)
- [X] T007 [P] Nueva `src/main/java/mx/personas/api/common/error/PersonaEliminadaException.java`
      (FR-014)
- [X] T008 [P] **Ajuste durante implementación**: no se creó
      `PersonaProfesionNoEncontradaException` — se reutiliza
      `RecursoNoEncontradoException` (mismo motivo que T005) para
      `PERSONA_PROFESION_NO_ENCONTRADA`, sin archivo nuevo
- [X] T009 [P] Nueva `src/main/java/mx/personas/api/common/error/PersonaProfesionYaRetiradaException.java`
      (nota: `PROFESION_NOMBRE_DUPLICADO` — caso "ya existe activa" —
      reutiliza la `DuplicateFieldException` ya existente, sin archivo nuevo)
- [X] T010 Nuevo `src/main/java/mx/personas/api/profesion/model/Profesion.java`:
      entidad JPA (tabla `profesion`), extiende `Auditable`; campos
      `nombre`/`descripcion`/`activo`; sin setter de `nombre` tras crearse
      (inmutable, FR-004); métodos `desactivar()`/`reactivar()`
- [X] T011 Nuevo `src/main/java/mx/personas/api/profesion/model/PersonaProfesion.java`:
      entidad JPA (tabla `persona_profesion`), extiende `Auditable`;
      `@ManyToOne` a `Persona` y a `Profesion`; campos
      `fechaDesde`/`cedula`/`activo`; método `retirar()` (solo
      `activo: true → false`, nunca al revés — research.md §4)
- [X] T012 Nuevo `src/main/java/mx/personas/api/profesion/repository/ProfesionRepository.java`:
      `Optional<Profesion> findByNombreNormalizado(String nombreNormalizado)`
      (query nativa usando `unaccent_immutable(lower(:nombre))`, misma
      normalización que el índice — research.md §6); `Page<Profesion>
      findByActivoTrue(Pageable)`; `Page<Profesion> findAll(Pageable)`
      (heredado, para `incluirInactivas` de ADMIN)
- [X] T013 Nuevo `src/main/java/mx/personas/api/profesion/repository/PersonaProfesionRepository.java`:
      `boolean existsByPersonaIdAndProfesionIdAndActivoTrue(UUID personaId,
      Long profesionId)` (FR-013); `List<PersonaProfesion>
      findByPersonaIdAndActivoTrue(UUID personaId)`; `List<PersonaProfesion>
      findByPersonaId(UUID personaId)` (incluye retiradas, para ADMIN —
      FR-017); `Optional<PersonaProfesion> findByIdAndPersonaId(UUID id, UUID
      personaId)` (para retirar/validar pertenencia)
- [X] T014 [P] Nuevo test `src/test/java/mx/personas/api/profesion/repository/ProfesionRepositoryIT.java`
      (Testcontainers, `unaccent` no existe en H2): crear "Mecánico" (ya
      sembrada) y confirmar que `findByNombreNormalizado("mecanico")` (sin
      acento, minúsculas) la encuentra; el índice único rechaza un `INSERT`
      directo de "MECÁNICO" duplicado (research.md §1)
- [X] T015 [P] Nuevo test `src/test/java/mx/personas/api/profesion/repository/PersonaProfesionRepositoryIT.java`
      (Testcontainers): dos filas `activo=true` para el mismo
      `(persona_id, profesion_id)` violan el índice único parcial; dos filas
      `activo=false` para el mismo par NO violan nada (research.md §3-4)

**Checkpoint**: migración aplicada, entidades y repositorios listos,
errores declarados. Ninguna historia tiene aún comportamiento observable vía
API.

---

## Phase 2: User Story 1 - Administrar el catálogo de profesiones (Priority: P1)

**Goal**: ADMIN puede crear, editar la descripción, desactivar y reactivar
profesiones; cualquier operador puede consultar el catálogo.

**Independent Test**: Crear una profesión nueva como ADMIN, editar su
descripción, desactivarla y reactivarla, confirmando el estado del catálogo
en cada paso — sin depender de que existan personas.

### Tests for User Story 1 ⚠️

- [X] T016 [P] [US1] Nuevo `@WebMvcTest`
      `src/test/java/mx/personas/api/profesion/ProfesionControllerTest.java`:
      CAPTURISTA → 403 en `POST/PATCH /api/profesiones/**` (crear, editar,
      desactivar, reactivar); ADMIN → invoca el `ProfesionService` mockeado
      normalmente (Acceptance Scenario 6); CAPTURISTA con `GET
      /api/profesiones?incluirInactivas=true` → 200 (no 403), y el
      `ProfesionService` mockeado se invoca con `incluirInactivas` forzado a
      `false` para ese rol (FR-010, hallazgo E5 de `/speckit-analyze`)
- [X] T017 [P] [US1] Nuevo integration test
      `src/test/java/mx/personas/api/integration/ProfesionCatalogoIT.java`
      (Testcontainers): ADMIN crea "Electricista" → 201 activa; crear
      "mecanico" (sin acento, minúsculas) → 409
      `PROFESION_NOMBRE_DUPLICADO` contra la semilla "Mecánico"; desactivar
      "Electricista" y crearla de nuevo → 409
      `PROFESION_NOMBRE_DESACTIVADA` con el id de la existente; reactivarla
      → vuelve a estar activa (Acceptance Scenarios 1-3, 5)

### Implementation for User Story 1

- [X] T018 [P] [US1] Nuevo
      `src/main/java/mx/personas/api/profesion/dto/ProfesionRequestDTO.java`:
      `nombre` (`@NotBlank`), `descripcion` opcional
- [X] T019 [P] [US1] Nuevo
      `src/main/java/mx/personas/api/profesion/dto/ProfesionUpdateDTO.java`:
      `descripcion` opcional (el nombre no se puede editar — FR-004)
- [X] T020 [P] [US1] Nuevo
      `src/main/java/mx/personas/api/profesion/dto/ProfesionResponseDTO.java`:
      `id, nombre, descripcion, activo`
- [X] T021 [P] [US1] Nuevo
      `src/main/java/mx/personas/api/profesion/dto/ProfesionPageResponseDTO.java`
      (mismo shape de paginación que `PersonaPageResponseDTO`)
- [X] T022 [US1] Nuevo
      `src/main/java/mx/personas/api/profesion/mapper/ProfesionMapper.java`
      (MapStruct): `Profesion → ProfesionResponseDTO`
- [X] T023 [US1] Nuevo
      `src/main/java/mx/personas/api/profesion/service/ProfesionService.java`:
      `crear(ProfesionRequestDTO)` (normaliza `unaccent+lower`, busca
      `findByNombreNormalizado`; si existe activa →
      `DuplicateFieldException(PROFESION_NOMBRE_DUPLICADO, ...)`; si existe
      desactivada → `ProfesionNombreDesactivadaException` con su id;
      research.md §6); `editarDescripcion(id, ProfesionUpdateDTO)`;
      `desactivar(id)`/`reactivar(id)` (`ProfesionNoEncontradaException` si
      no existe); `listarCatalogo(pageable, incluirInactivas, esAdmin)`
      (`incluirInactivas` solo tiene efecto si `esAdmin` — FR-010)
- [X] T024 [US1] Nuevo
      `src/main/java/mx/personas/api/profesion/controller/ProfesionController.java`:
      `POST /api/profesiones` (`hasRole('ADMIN')`), `PATCH
      /api/profesiones/{id}` (`hasRole('ADMIN')`, editar descripción),
      `PATCH /api/profesiones/{id}/desactivar` (`hasRole('ADMIN')`), `PATCH
      /api/profesiones/{id}/reactivar` (`hasRole('ADMIN')`), `GET
      /api/profesiones` (`hasAnyRole('ADMIN','CAPTURISTA')`, paginado,
      `incluirInactivas` query param)

**Checkpoint**: catálogo de profesiones funcional e independientemente
verificable.

---

## Phase 3: User Story 2 - Asignar una profesión a una persona (Priority: P1)

**Goal**: ADMIN o CAPTURISTA pueden asignar una profesión activa del
catálogo a una persona activa.

**Independent Test**: Con al menos una profesión activa en el catálogo,
asignarla a una persona existente y confirmar la asignación creada.

### Tests for User Story 2 ⚠️

- [X] T025 [P] [US2] Nuevo integration test
      `src/test/java/mx/personas/api/integration/PersonaProfesionAsignarIT.java`
      (Testcontainers): asignar "Mecánico" (semilla) a una persona activa →
      201 con `fechaDesde` = hoy por defecto; asignar la misma profesión de
      nuevo → 409 `PERSONA_PROFESION_YA_ASIGNADA`; asignar una profesión
      desactivada → 409 `PROFESION_DESACTIVADA`; asignar a una persona
      eliminada lógicamente → 409 `PERSONA_ELIMINADA`; CAPTURISTA puede
      asignar con normalidad (Acceptance Scenarios 1, 3, 4, 5); tras asignar,
      **consultar `GET /api/personas/{id}/historial` como ADMIN y confirmar
      una entrada `MODIFICACION` con la profesión asignada** (FR-024,
      hallazgo E4 de `/speckit-analyze`); **crear una profesión nueva,
      asignarla a una persona, desactivarla en el catálogo, y confirmar que
      la persona la conserva en `GET /api/personas/{id}/profesiones`**
      (FR-008, hallazgo E2 de `/speckit-analyze` — desactivar nunca modifica
      asignaciones existentes; este escenario vive aquí y no en `T017` para
      no romper la independencia de US1)

### Implementation for User Story 2

- [X] T026 [P] [US2] Nuevo
      `src/main/java/mx/personas/api/profesion/dto/AsignacionProfesionRequestDTO.java`:
      `profesionId` (`@NotNull`), `fechaDesde` opcional, `cedula` opcional
- [X] T027 [P] [US2] Nuevo
      `src/main/java/mx/personas/api/profesion/dto/AsignacionProfesionResponseDTO.java`:
      `id, profesionId, profesionNombre, fechaDesde, cedula, activo`
- [X] T028 [US2] Modificar
      `src/main/java/mx/personas/api/persona/service/HistorialDiffService.java`:
      nuevo método `serializarAsignacionProfesion(PersonaProfesion)` →
      `List<CampoCambiadoDTO>` (registra profesión asignada, fecha desde,
      cédula) para la entrada `MODIFICACION` de `persona_historial`
      (research.md §5)
- [X] T029 [US2] Nuevo
      `src/main/java/mx/personas/api/profesion/service/PersonaProfesionService.java`:
      `asignar(personaId, AsignacionProfesionRequestDTO, usuarioId)` — valida
      persona activa (`PersonaEliminadaException` si no), profesión activa
      (`ProfesionNoEncontradaException`/`ProfesionDesactivadaException`), sin
      asignación activa duplicada
      (`PersonaProfesionYaAsignadaException`); crea la fila, registra
      `persona_historial` vía `HistorialDiffService`
- [X] T030 [US2] Modificar
      `src/main/java/mx/personas/api/persona/controller/PersonaController.java`:
      `POST /api/personas/{id}/profesiones`
      (`hasAnyRole('ADMIN','CAPTURISTA')`), inyecta `PersonaProfesionService`

**Checkpoint**: US1 y US2 funcionan de forma independiente entre sí.

---

## Phase 4: User Story 3 - Consultar las profesiones de una persona (Priority: P1)

**Goal**: Cualquier operador puede consultar las profesiones asignadas a una
persona, con los datos propios de cada asignación.

**Independent Test**: Con una persona que ya tiene profesiones asignadas,
consultar su listado y confirmar que aparecen con sus datos.

### Tests for User Story 3 ⚠️

- [X] T031 [P] [US3] Nuevo integration test
      `src/test/java/mx/personas/api/integration/PersonaProfesionConsultarIT.java`
      (Testcontainers): persona con "Mecánico" y "Electricista" activas →
      la consulta regresa ambas con `fechaDesde`/`cedula`; persona sin
      profesiones → lista vacía (no error); `incluirRetiradas=true` solo
      tiene efecto para ADMIN (Acceptance Scenarios 1-2, FR-017)

### Implementation for User Story 3

- [X] T032 [US3] Modificar `PersonaProfesionService.java`:
      `listarPorPersona(personaId, incluirRetiradas, esAdmin)` →
      `List<AsignacionProfesionResponseDTO>`
- [X] T033 [US3] Modificar `PersonaController.java`: `GET
      /api/personas/{id}/profesiones` (`hasAnyRole('ADMIN','CAPTURISTA')`,
      query param `incluirRetiradas`)

**Checkpoint**: US1, US2 y US3 funcionan de forma independiente.

---

## Phase 5: User Story 4 - Consultar el directorio de personas por profesión (Priority: P1)

**Goal**: Cualquier operador puede consultar el listado paginado de personas
activas con una asignación activa de una profesión, con un DTO reducido.

**Independent Test**: Con una persona activa con una asignación activa de
una profesión, consultar el directorio de esa profesión y confirmar que
aparece.

### Tests for User Story 4 ⚠️

- [X] T034 [P] [US4] Nuevo integration test
      `src/test/java/mx/personas/api/integration/ProfesionDirectorioIT.java`
      (Testcontainers): persona activa con "Mecánico" activa → aparece en
      el directorio con `id, nombreCompleto, fechaDesde, cedula` (sin
      correo/teléfono/CURP/RFC/dirección); persona eliminada lógicamente
      con "Mecánico" activa → NO aparece; **restaurarla (`POST
      /api/personas/{id}/restaurar`) → reaparece en el directorio con la
      misma asignación** (FR-019/SC-006, hallazgo E3 de
      `/speckit-analyze` — la otra mitad del edge case, no solo la
      desaparición); resultado paginado (Acceptance Scenarios 1-3)

### Implementation for User Story 4

- [X] T035 [P] [US4] Nuevo
      `src/main/java/mx/personas/api/profesion/dto/PersonaDirectorioDTO.java`:
      `id (UUID de persona), nombreCompleto, fechaDesde, cedula` — nunca
      otros datos personales (FR-018)
- [X] T036 [P] [US4] Nuevo
      `src/main/java/mx/personas/api/profesion/dto/PersonaDirectorioPageResponseDTO.java`
- [X] T037 [US4] Modificar `PersonaProfesionRepository.java`: query
      (JPQL con `JOIN` a `Persona`) `Page<PersonaProfesion>
      findDirectorioByProfesionId(Long profesionId, Pageable pageable)` —
      filtra `persona_profesion.activo = true` **y** `persona.activo =
      true` (FR-018/FR-019)
- [X] T038 [US4] Modificar `ProfesionService.java` (o
      `PersonaProfesionService.java`): `directorio(profesionId, pageable)` →
      mapea a `PersonaDirectorioDTO` (`nombreCompleto` = `nombres +
      apellidos`, nunca el resto de campos de `Persona`)
- [X] T039 [US4] Modificar `ProfesionController.java`: `GET
      /api/profesiones/{id}/personas` (`hasAnyRole('ADMIN','CAPTURISTA')`,
      paginado)

**Checkpoint**: US1-US4 funcionan de forma independiente entre sí.

---

## Phase 6: User Story 5 - Retirar la asignación de una profesión a una persona (Priority: P2)

**Goal**: ADMIN o CAPTURISTA pueden retirar (desactivar) una asignación
activa sin borrarla.

**Independent Test**: Con una persona con una profesión asignada de forma
activa, retirársela y confirmar que deja de aparecer en el directorio de esa
profesión.

### Tests for User Story 5 ⚠️

- [X] T040 [P] [US5] Nuevo integration test
      `src/test/java/mx/personas/api/integration/PersonaProfesionRetirarIT.java`
      (Testcontainers): **autenticado como CAPTURISTA** (FR-022, hallazgo E6
      de `/speckit-analyze` — retirar también está permitido a CAPTURISTA,
      no solo a ADMIN), retirar una asignación activa → 200, deja de
      aparecer en el directorio de esa profesión, conserva el resto de
      profesiones de la persona; tras retirar, **consultar `GET
      /api/personas/{id}/historial` como ADMIN y confirmar una entrada
      `MODIFICACION` del retiro** (FR-024, hallazgo E4 de
      `/speckit-analyze`); ADMIN consulta el histórico
      (`incluirRetiradas=true`) y la ve; retirar de nuevo la misma
      asignación → 409 `PERSONA_PROFESION_YA_RETIRADA`; retirar un id de
      asignación inexistente → 404 `PERSONA_PROFESION_NO_ENCONTRADA`;
      **retirar "Mecánico" y volver a asignárselo a la misma persona →
      se crea una asignación NUEVA (fila distinta), la retirada anterior
      permanece intacta como episodio histórico separado, y ambas son
      consultables por ADMIN vía `incluirRetiradas=true`** (FR-013,
      research.md §4, hallazgo E1 de `/speckit-analyze` — la decisión
      central de `/speckit-clarify` para este feature) (Acceptance
      Scenarios 1-2)

### Implementation for User Story 5

- [X] T041 [US5] Modificar `HistorialDiffService.java`: nuevo método
      `serializarRetiroProfesion(PersonaProfesion)` → entrada
      `MODIFICACION` de `persona_historial` (research.md §5)
- [X] T042 [US5] Modificar `PersonaProfesionService.java`:
      `retirar(personaId, asignacionId, usuarioId)` —
      `PersonaProfesionNoEncontradaException` si no existe o no pertenece a
      la persona; `PersonaProfesionYaRetiradaException` si ya estaba
      `activo = false`; marca `activo = false` (nunca borra, nunca crea
      fila nueva al retirar — solo al reasignar, research.md §4) y registra
      `persona_historial`
- [X] T043 [US5] Modificar `PersonaController.java`: `PATCH
      /api/personas/{id}/profesiones/{asignacionId}/retirar`
      (`hasAnyRole('ADMIN','CAPTURISTA')`)

**Checkpoint**: las 5 historias funcionan juntas de punta a punta.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Verificación final de que el feature completo cumple spec y
constitución

- [X] T044 Ejecutar manualmente `quickstart.md` de punta a punta contra la
      aplicación levantada localmente
- [X] T045 Correr la suite completa (`mvn test` y `mvn verify` para los
      `*IT.java`) y confirmar 100% verde, incluida la suite ya adaptada por
      features anteriores (FR-025, SC-008)
- [X] T046 [P] Confirmar contra `/v3/api-docs` que ningún endpoint existente
      (`/api/personas/**`, `/api/codigos-postales/**`, `/api/usuarios/**`)
      cambió de schema (Principio II)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: sin dependencias externas — BLOQUEA a las 5
  historias
- **US1 (Phase 2)**: depende de Foundational; independiente de US2-US5
- **US2 (Phase 3)**: depende de Foundational (usa `PersonaProfesion`,
  `ProfesionRepository` para validar estado); independiente de US1, US3,
  US4, US5 en cuanto a su propio valor, aunque necesita que exista al menos
  una profesión en el catálogo para su Independent Test (la semilla
  "Mecánico" ya la provee Foundational, sin depender de que US1 esté
  implementada)
- **US3 (Phase 4)**: depende de que existan asignaciones para tener algo que
  consultar en su Independent Test (las produce US2 en tiempo de ejecución),
  pero su propia implementación (el endpoint `GET`) no depende del código de
  US2
- **US4 (Phase 5)**: mismo caso que US3 — depende de datos que US2 produce,
  no de su código
- **US5 (Phase 6)**: depende de que existan asignaciones activas (las
  produce US2) para tener algo que retirar en su Independent Test
- **Polish (Phase 7)**: depende de que todas las historias deseadas estén
  completas

### Parallel Opportunities

- Dentro de Foundational: T002 (ErrorCode) en paralelo con T003-T009
  (excepciones, archivos distintos) y con T014-T015 (tests, una vez existan
  T010-T013)
- **US3, US4 y US5 pueden implementarse en paralelo** una vez completada
  US2 (todas dependen de que existan asignaciones, no entre sí)
- Dentro de cada fase, las tareas marcadas `[P]` pueden ejecutarse en
  paralelo

## Implementation Strategy

### MVP First (US1 + US2)

1. Completar Foundational (T001-T015)
2. Completar US1 (T016-T024) — catálogo administrado
3. Completar US2 (T025-T030) — asignar profesiones, el objetivo central del
   feature
4. **DETENERSE y VALIDAR**: confirmar `ProfesionCatalogoIT` y
   `PersonaProfesionAsignarIT` en verde

### Entrega incremental

1. Foundational → migración + entidades + repositorios + errores
2. US1 → catálogo administrado (ADMIN)
3. US2 → asignar profesiones (MVP funcional)
4. US3 → consultar profesiones de una persona
5. US4 → directorio por profesión
6. US5 → retirar asignaciones
7. Polish → validación de punta a punta

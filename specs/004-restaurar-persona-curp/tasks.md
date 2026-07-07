---

description: "Task list for Restaurar Personas y Unicidad de CURP Global"
---

# Tasks: Restaurar Personas y Unicidad de CURP Global

**Input**: Design documents from `/specs/004-restaurar-persona-curp/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Dependencias satisfechas**: `002-autenticacion-autorizacion` y `003-auditoria-personas`
ya están implementados (roles, `SecurityContext`, `PersonaHistorial`/`HistorialDiffService`).
Las tareas de este archivo son ejecutables.

**⚠️ Reconciliación con `003` (ver plan.md § Reconciliación)**: `003` ya implementó
`PATCH /api/personas/{id}/restaurar` con un chequeo de conflicto que incluía tanto
`correo` como `curp` contra activos. Este feature **reemplaza** esa implementación: el
verbo pasa a `POST`, y el chequeo de CURP se **elimina por completo** (FR-010 — la
unicidad global de CURP, introducida aquí, hace ese conflicto estructuralmente
imposible). Las tareas de la Fase 4 (US3) modifican código y tests que ya existen de
`003`, no agregan una capacidad nueva desde cero.

**Tests**: INCLUDED. El Principio III de la constitución
(`.specify/memory/constitution.md`) es Test-First con Suite Siempre Verde y
NON-NEGOTIABLE. El plan exige explícitamente un test de que la migración falla ante CURP
duplicados preexistentes (Constitution Check, Principio III).

**Organization**: Tareas agrupadas por historia de usuario (spec.md) para permitir
implementación y prueba independiente de cada una.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: Historia de usuario a la que pertenece (US1..US4)
- Cada tarea incluye la ruta de archivo exacta

## Path Conventions

Proyecto único Maven/Spring Boot (ver `plan.md` → Project Structure):

- `src/main/java/mx/personas/api/...`
- `src/main/resources/db/migration/...`
- `src/test/java/mx/personas/api/...`

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: La migración de CURP a unicidad global y los métodos de repositorio nuevos
que las 4 historias de usuario necesitan.

**⚠️ CRITICAL**: Ninguna historia de usuario puede implementarse hasta completar esta fase

- [X] T001 Migración
      `src/main/resources/db/migration/V4__globalizar_unicidad_curp.sql`: bloque `DO $$
      ... END $$` que cuenta CURP duplicados entre **todos** los registros (activos e
      inactivos) y lanza `RAISE EXCEPTION` explícito si hay alguno; luego `DROP INDEX
      ux_persona_curp_activo`; luego `ALTER TABLE persona ADD CONSTRAINT uq_persona_curp
      UNIQUE (curp)` (research.md §2; posterior a `V3` de `003`, no se toca `V1`/`V2`/`V3`)
- [X] T002 [P] Agregar a `src/main/java/mx/personas/api/common/error/ErrorCode.java`:
      `PERSONA_CURP_ELIMINADA(HttpStatus.CONFLICT)` (FR-004, 409 accionable)
- [X] T003 Modificar
      `src/main/java/mx/personas/api/persona/repository/PersonaRepository.java`: agregar
      `Optional<Persona> findByCurp(String curp)` (sin filtro de `activo` — research.md
      §3), `Optional<Persona> findByCorreoAndActivoTrue(String correo)` (para incluir el
      `id` de la persona activa en el mensaje accionable de `restaurar`, FR-009), y
      `Page<Persona> findByActivoFalse(Pageable pageable)` (US4, simétrico a
      `buscarActivas`); **quitar** `existsByCurpAndActivoTrue` y
      `existsByCurpAndActivoTrueAndIdNot` (código muerto tras T013 — reemplazados por
      `findByCurp`)
- [X] T004 [P] Nuevo DTO
      `src/main/java/mx/personas/api/persona/dto/PersonaEliminadaPageResponseDTO.java`:
      `List<PersonaResponseDTO> contenido, int pagina, int tamanoPagina, long
      totalElementos, int totalPaginas` (mismo shape de paginación que
      `PersonaPageResponseDTO`, pero con el DTO de persona **completo** — incluidos
      campos de auditoría de `003` — en vez del resumen sin auditoría que usa el listado
      general; data-model.md § vista de eliminados)
- [X] T005 [P] Extender
      `src/test/java/mx/personas/api/common/error/GlobalExceptionHandlerTest.java`: test
      directo para `DuplicateFieldException` con `ErrorCode.PERSONA_CURP_ELIMINADA` → 409,
      mismo patrón que los demás códigos ya cubiertos en ese archivo (FR-004)
- [X] T006 Nuevo test `MigracionCurpGlobalIT` en
      `src/test/java/mx/personas/api/integration/MigracionCurpGlobalIT.java`: **no**
      extiende `AbstractIntegrationTest` (ese arranca el contexto completo y aplica todas
      las migraciones automáticamente); usa su propio Testcontainers `PostgreSQLContainer`
      + la API de Flyway directamente — aplica migraciones solo hasta `V3`, siembra dos
      filas de `persona` con la misma CURP (una activa, una `activo=false`), y confirma
      que ejecutar `V4` lanza una excepción y dicha fila no queda aplicada (Constitution
      Check de plan.md, Principio III; research.md §2)

**Checkpoint**: Migración aplicada y validada; `findByCurp`/`findByCorreoAndActivoTrue`/
`findByActivoFalse` disponibles. Ninguna historia de usuario tiene aún su comportamiento
observable implementado.

---

## Phase 2: User Story 1 - El correo de una persona eliminada puede reutilizarse (Priority: P1) 🎯 MVP

**Goal**: Confirmar (sin cambio de código — D3 ya vigente) que el correo de una persona
eliminada lógicamente puede ser tomado por una persona nueva, y que el conflicto
activo-contra-activo sigue funcionando igual.

**Independent Test**: Eliminar lógicamente una persona con correo `X`, dar de alta una
persona nueva con correo `X`, confirmar que procede sin error.

### Tests for User Story 1 ⚠️

- [X] T007 [P] [US1] Nuevo integration test `PersonaCorreoReutilizableIT` en
      `src/test/java/mx/personas/api/integration/PersonaCorreoReutilizableIT.java`: crear
      persona con correo `X` → eliminarla lógicamente → crear una persona nueva con correo
      `X` → 201 (Acceptance Scenario US1 #1); dos personas activas intentando compartir
      correo → 409 `PERSONA_CORREO_DUPLICADO` sin cambios (Acceptance Scenario US1 #2)

### Implementation for User Story 1

*(Ninguna: D3 ya está vigente en el código actual — `existsByCorreoAndActivoTrue` ya
filtra por `activo = true`. Esta historia solo se verifica con un test.)*

**Checkpoint**: US1 confirmado independientemente.

---

## Phase 3: User Story 2 - La CURP es identidad permanente y guía hacia la restauración (Priority: P1)

**Goal**: Al crear o actualizar una persona con una CURP que ya pertenece a otro
registro, el sistema distingue si ese registro está activo (409 sin cambios) o eliminado
(409 accionable nuevo, con el `id` del registro eliminado, sin otros datos personales).

**Independent Test**: Eliminar lógicamente una persona con CURP `Y`, intentar registrar
una persona nueva con CURP `Y`, confirmar el 409 accionable sin datos personales del
registro eliminado.

### Tests for User Story 2 ⚠️

- [X] T008 [P] [US2] Extender
      `src/test/java/mx/personas/api/persona/PersonaControllerCreateTest.java`: nuevo test
      `curpDeRegistroEliminadoRegresa409Accionable` — el service mockeado lanza
      `DuplicateFieldException(ErrorCode.PERSONA_CURP_ELIMINADA, ...)`, asertar `codigo` y
      que `detalles[0].motivo` no contiene nombres/correo/teléfono, solo referencia un
      `id` (FR-004, Edge Cases)
- [X] T009 [P] [US2] Extender
      `src/test/java/mx/personas/api/persona/PersonaControllerUpdateTest.java`: agregar
      `curpDuplicadaActivaRegresa409` (caso activo-contra-activo, no cubierto hoy en este
      archivo) y `curpDeRegistroEliminadoRegresa409Accionable` (FR-002, FR-003, FR-004)
- [X] T010 [P] [US2] Extender
      `src/test/java/mx/personas/api/persona/PersonaServiceTest.java`: unit tests para el
      nuevo helper de validación de CURP vía `crear()`/`actualizar()` — CURP de un
      registro **activo** → `PERSONA_CURP_DUPLICADO` (mensaje sin cambios); CURP de un
      registro **eliminado** → `PERSONA_CURP_ELIMINADA` con el `id` de ese registro en
      `detalles[0].motivo`; CURP libre → sin excepción; actualizar la propia CURP sin
      cambiarla → sin excepción (research.md §3)
- [X] T011 [P] [US2] Nuevo integration test `PersonaCurpGlobalIT` en
      `src/test/java/mx/personas/api/integration/PersonaCurpGlobalIT.java`: crear +
      eliminar persona con CURP `Y` → crear nueva persona con CURP `Y` → 409
      `PERSONA_CURP_ELIMINADA`, `detalles[0].motivo` contiene el `id` del registro
      eliminado y ningún otro dato personal → actualizar otra persona asignándole la CURP
      `Y` → mismo 409 (Acceptance Scenarios US2 #1-#2); dos personas **activas**
      compartiendo CURP → `409 PERSONA_CURP_DUPLICADO` sin cambios (Acceptance Scenario
      US2 #3); crear una persona cuyo correo **y** cuya CURP pertenecen ambos al mismo
      registro eliminado → responde el 409 de CURP (`PERSONA_CURP_ELIMINADA`), nunca un
      conflicto de correo (spec Edge Cases)

### Implementation for User Story 2

- [X] T012 [US2] `PersonaService`: nuevo método privado
      `validarCurpDisponible(String curp, UUID idAExcluir)` — busca `findByCurp(curp)`;
      si no hay resultado, o el resultado es la misma persona (`idAExcluir`), no hace
      nada; si el resultado está activo, lanza `DuplicateFieldException
      (PERSONA_CURP_DUPLICADO, ...)` con el mismo mensaje que hoy; si está eliminado,
      lanza `DuplicateFieldException(PERSONA_CURP_ELIMINADA, "curp", "Existe un registro
      eliminado con este CURP; un ADMIN puede restaurarlo", "Registro eliminado con id "
      + id)` (research.md §3, contracts/personas-restaurar-api.md)
- [X] T013 [US2] `PersonaService`: en `crear(...)`, reemplazar el chequeo de CURP dentro
      de `validarNoDuplicado(...)` por una llamada a `validarCurpDisponible(dto.curp(),
      null)` (separando la validación de `correo`, que no cambia); en `actualizar(...)`,
      reemplazar `existsByCurpAndActivoTrueAndIdNot(...)` por `validarCurpDisponible
      (dto.curp(), id)` dentro del mismo bloque `if (dto.curp() != null && !dto.curp()
      .equals(persona.getCurp()))` (FR-002 a FR-004)

**Checkpoint**: US1+US2 funcionales juntas — reglas de unicidad de correo/CURP completas
y verificables de punta a punta.

---

## Phase 4: User Story 3 - Restaurar una persona eliminada lógicamente (Priority: P1)

**Goal**: `POST /api/personas/{id}/restaurar` (ADMIN) reactiva una persona validando
**solo** conflicto de correo contra activos (nunca CURP, por construcción — FR-010),
indicando el `id` de la persona activa en caso de conflicto de correo.

**Independent Test**: Eliminar una persona, restaurarla como ADMIN, confirmar que vuelve
a estar activa con sus datos y dirección intactos.

**Nota**: esta fase **modifica** código y tests ya existentes de `003` (ver
Reconciliación arriba), no agrega un endpoint desde cero.

### Tests for User Story 3 ⚠️

- [X] T014 [US3] Modificar
      `src/test/java/mx/personas/api/persona/PersonaControllerRestaurarTest.java`:
      cambiar todas las llamadas `patch("/api/personas/{id}/restaurar", id)` por
      `post(...)`; actualizar `restaurarConCorreoYaTomadoPorOtraPersonaActivaRegresa409`
      para asertar que `detalles[0].motivo` contiene un `id` (nuevo formato del mensaje —
      FR-009); **no** agregar ningún caso de conflicto de CURP al restaurar (FR-010 lo
      prohíbe estructuralmente)
- [X] T015 [US3] Modificar
      `src/test/java/mx/personas/api/integration/PersonaRestaurarIT.java`: cambiar
      `HttpMethod.PATCH` por `HttpMethod.POST` en las llamadas a `.../restaurar`;
      extender `eliminarYRestaurarCicloCompleto` para capturar el cuerpo completo de la
      persona (incluida `direccion`) antes de eliminar, y asertar que el cuerpo tras
      restaurar es campo-por-campo idéntico (nombres/apellidos/fechaNacimiento/sexo/
      curp/rfc/correo/telefono/direccion.*) — SC-004; extender ese mismo test para
      eliminar y restaurar una segunda vez sobre la misma persona, y confirmar que el
      historial tiene exactamente 2×`ELIMINACION` + 2×`RESTAURACION`, sin duplicados
      (spec Edge Cases); actualizar `restaurarConCorreoYaTomadoPorOtraPersonaActivaRegresa409`
      para asertar que `detalles[0].motivo` contiene el `id` de la persona activa que
      ocupa el correo (Acceptance Scenario US3 #4), y agregar un `GET` posterior al
      intento fallido confirmando que la persona sigue exactamente igual (sin activarse,
      sin cambios) — SC-006
- [X] T016 [P] [US3] Extender
      `src/test/java/mx/personas/api/persona/PersonaServiceTest.java`: el test de
      restaurar exitoso ya no debe invocar ningún chequeo de CURP (verificar que
      `personaRepository.findByCurp(...)` nunca se llama dentro de `restaurar(...)`);
      extender el test de conflicto de correo para asertar que `detalles[0].motivo`
      incluye el `id` de la persona activa, **y que `persona.isActivo()` sigue siendo
      `false` después de la excepción** (la reactivación nunca se ejecutó — SC-006)

### Implementation for User Story 3

- [X] T017 [US3] `PersonaController`: cambiar `@PatchMapping("/{id}/restaurar")` por
      `@PostMapping("/{id}/restaurar")` (corrige el verbo asumido por `003` — research.md
      §5)
- [X] T018 [US3] `PersonaService.restaurar(...)`: **quitar por completo** el chequeo de
      `existsByCurpAndActivoTrue(...)` (FR-010 — imposible por construcción tras US2);
      reemplazar el chequeo de correo por `findByCorreoAndActivoTrue(persona.getCorreo())`
      para incluir el `id` de esa persona activa en
      `DuplicateFieldException(PERSONA_CORREO_DUPLICADO, "correo", "...", "En uso por la
      persona activa con id " + id)` (FR-009, research.md §4)

**Checkpoint**: US1+US2+US3 funcionales juntas — el ciclo eliminar→restaurar es
verificable de punta a punta con las reglas correctas de conflicto.

---

## Phase 5: User Story 4 - Ver qué personas están eliminadas (Priority: P2)

**Goal**: `GET /api/personas/eliminadas` (ADMIN) muestra, paginado, solo las personas con
`activo = false`, como vista separada del listado general.

**Independent Test**: Con al menos una persona eliminada y una activa, confirmar como
ADMIN que solo la eliminada aparece en esta vista.

### Tests for User Story 4 ⚠️

- [X] T019 [P] [US4] Nuevo `@WebMvcTest` `PersonaControllerEliminadasTest` en
      `src/test/java/mx/personas/api/persona/PersonaControllerEliminadasTest.java`:
      `@WithMockUser(roles="ADMIN")` → 200 con el shape paginado;
      `@WithMockUser(roles="CAPTURISTA")` → 403 `ACCESO_DENEGADO` (FR-012, FR-013)
- [X] T020 [P] [US4] Nuevo integration test `PersonaEliminadasIT` en
      `src/test/java/mx/personas/api/integration/PersonaEliminadasIT.java`: sembrar una
      persona activa y una eliminada lógicamente → `GET /api/personas/eliminadas` como
      ADMIN solo incluye la eliminada → mismo endpoint como CAPTURISTA → 403 (Acceptance
      Scenarios US4 #1-#2)

### Implementation for User Story 4

- [X] T021 [US4] `PersonaService.listarEliminadas(Pageable pageable)`: pagina
      `personaRepository.findByActivoFalse(pageable)`, mapea cada fila con
      `personaMapper.toResponseDTO(persona, obtenerDireccionVigente(persona))` (shape
      completo, no el resumen del listado general — data-model.md), construye
      `PersonaEliminadaPageResponseDTO`
- [X] T022 [US4] `PersonaController`: `GET /api/personas/eliminadas`,
      `@PreAuthorize("hasRole('ADMIN')")` (FR-012, FR-013)

**Checkpoint**: Las 4 historias de usuario funcionan juntas de punta a punta.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Verificación final de que el feature completo cumple spec y constitución

- [X] T023 Ejecutar manualmente `quickstart.md` de punta a punta contra la aplicación
      levantada localmente (con `002`/`003` también funcionando) y ajustar el propio
      `quickstart.md` si algún paso no coincide con el comportamiento real implementado
- [X] T024 Correr la suite completa (`mvn test` y `mvn verify` para los `*IT.java`) y
      confirmar 100% verde, incluida la suite ya adaptada por `002`/`003` (FR-014, SC-007)
- [X] T025 [P] Auditar el repo (`grep -rn "PATCH.*restaurar\|existsByCurpAndActivoTrue"
      src/`) y confirmar que no queda ninguna referencia activa al verbo `PATCH` para
      restaurar ni a los métodos de repositorio retirados en T003

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: Depende de que `002`/`003` estén implementados (ya lo
  están) — BLOQUEA las 4 historias de usuario
- **US1 (Phase 2)**: Depende solo de Foundational; su comportamiento ya existe en el
  código actual (D3), esta fase solo lo verifica
- **US2 (Phase 3)**: Depende de Foundational (`findByCurp`, T002-T003)
- **US3 (Phase 4)**: Depende de Foundational y de que US2 exista conceptualmente (FR-010
  asume la unicidad global de CURP ya vigente), aunque su implementación (T017-T018) es
  independiente del código de T012-T013
- **US4 (Phase 5)**: Depende de Foundational (`findByActivoFalse`,
  `PersonaEliminadaPageResponseDTO`); independiente de US1-US3
- **Polish (Phase 6)**: Depende de que las 4 historias estén completas

### User Story Dependencies

- **US1 (P1)**: Ninguna dependencia — ya vigente, solo se verifica
- **US2 (P1)**: Depende de Foundational; independiente de US1/US3/US4
- **US3 (P1)**: Depende de Foundational; reconcilia código ya existente de `003`, por lo
  que sus tareas de test (T014-T015) son modificaciones, no archivos nuevos
- **US4 (P2)**: Depende de Foundational; independiente de US1-US3

### Within Each User Story

- Tests escritos/modificados y en rojo antes de la implementación correspondiente
- US3 modifica archivos existentes: verificar que los tests fallan con el código viejo
  (verbo `PATCH`, chequeo de CURP) antes de aplicar T017-T018

### Parallel Opportunities

- Dentro de Foundational: T002, T004, T005 en paralelo; T003 y T006 dependen de la
  migración/entorno, no estrictamente en paralelo con lo demás
- Dentro de US2: T008-T011 en paralelo (archivos distintos)
- Dentro de US4: T019-T020 en paralelo

---

## Implementation Strategy

### MVP First (User Story 1 + 2 — la regla de unicidad completa)

1. Completar Fase 1: Foundational
2. Completar Fase 2: US1 (verificación)
3. Completar Fase 3: US2 (CURP accionable)
4. **DETENER y VALIDAR**: probar US1+US2 de forma independiente (quickstart.md §2-3)

### Incremental Delivery

1. Foundational → migración de CURP aplicada y validada
2. + US1 → confirma D3 (correo reutilizable)
3. + US2 → CURP identidad permanente con 409 accionable
4. + US3 → restaurar reconciliado (`POST`, sin chequeo de CURP)
5. + US4 → vista de eliminados
6. Cada historia agrega valor sin romper a las anteriores (suite siempre verde)

---

## Notes

- [P] tareas = archivos distintos, sin dependencias pendientes
- [Story] etiqueta cada tarea con su historia de usuario para trazabilidad
- T014-T015-T018 **modifican** código/tests de `003`, no agregan una capacidad paralela —
  ver la nota de Reconciliación al inicio de este archivo
- `PersonaEliminadaPageResponseDTO` (T004) usa `PersonaResponseDTO` completo (con
  auditoría) por elemento, a diferencia de `PersonaPageResponseDTO` (listado general, que
  desde `003` usa `PersonaResumenDTO` sin auditoría) — son vistas con propósitos
  distintos (data-model.md)

# Implementation Plan: Restaurar Personas y Unicidad de CURP Global

**Branch**: `004-restaurar-persona-curp` | **Date**: 2026-07-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-restaurar-persona-curp/spec.md`

## ⚠️ Dependencias y reconciliación con `002`/`003`

- Requiere `002-autenticacion-autorizacion` implementado (roles, `SecurityContext`).
- Requiere `003-auditoria-personas` implementado, específicamente `persona_historial` y
  el servicio de diff, para registrar la operación `RESTAURACION` (FR-011). Ninguno de
  los dos está implementado todavía en el repositorio (solo especificados/planeados).
- **Reconciliación explícita**: `003` ya había especificado y planeado su propia
  capacidad de restauración (su US3: `PATCH /api/personas/{id}/restaurar`, con la CURP
  pudiendo conflictuar al restaurar). Este plan **reemplaza/supersede** esa parte de
  `003` — implementa `POST /api/personas/{id}/restaurar` con las reglas más precisas de
  `004` (correo puede conflictuar, CURP nunca). Al ejecutar `/speckit-tasks` para `003`,
  sus tareas relacionadas con restaurar (FR-013 a FR-015, US3) deben omitirse o
  actualizarse para no duplicar/contradecir lo que este plan construye. El resto de
  `003` (auditoría básica, historial de creación/modificación/eliminación) no se ve
  afectado.

## Corrección de premisa (ver sesión de planeación)

La instrucción original de este plan asumía que `correo` tenía una restricción
`uq_persona_correo` **global** a reemplazar por una parcial, y que la CURP **ya** era
global. La inspección de `V1__create_schema.sql` mostró lo contrario: hoy **ambas**
(`correo` y `curp`) son únicas solo entre activos, vía índices parciales
(`ux_persona_correo_activo`, `ux_persona_curp_activo`); no existe ningún
`uq_persona_correo`, y ningún `@Column` declara `unique = true` (la unicidad siempre
vivió en Flyway, nunca en JPA — ver `specs/001.../research.md` §2). En consecuencia:

- **`correo`** ya cumple D3 tal cual está hoy — **no requiere ninguna migración**.
- **`curp`** es la que realmente necesita migrar, de índice parcial a `UNIQUE` global
  (D2) — es el único cambio de esquema real de este feature.

Este plan se escribe con esa corrección (confirmada por el usuario durante la
planeación); ver `research.md` §1 para el detalle completo.

## Summary

Migrar `persona.curp` de único-solo-entre-activos a único de forma global (D2),
mediante una migración Flyway aditiva que primero verifica que no existan CURP
duplicados entre **todos** los registros (activos e inactivos) y falla explícitamente
si los hay. `persona.correo` no cambia (D3 ya vigente). En el `service`, el alta y la
actualización de personas distinguen, ante un conflicto de CURP, si el registro
existente está activo (409 de duplicado, sin cambios) o eliminado lógicamente (409
accionable nuevo, indicando que un ADMIN puede restaurarlo). Se añade
`POST /api/personas/{id}/restaurar` (ADMIN) que reactiva una persona validando
únicamente conflicto de correo contra activos (la CURP nunca puede conflictuar, por
construcción), y `GET /api/personas/eliminadas` (ADMIN) como vista dedicada y separada
del listado general.

## Technical Context

**Language/Version**: Java 21 (existente)

**Primary Dependencies**: Spring Boot 3.3.5 / Spring Data JPA (existente), Flyway
(existente), Spring Security 6 (de `002`, para `@PreAuthorize` ADMIN), infraestructura
de historial de `003` (`PersonaHistorial`, `HistorialDiffService`) para registrar
`RESTAURACION`

**Storage**: PostgreSQL (existente) — 0 tablas nuevas; 1 migración que reemplaza un
índice parcial por una restricción `UNIQUE` global sobre `persona.curp`

**Testing**: JUnit 5 + Mockito + Testcontainers/PostgreSQL (existente; la migración y su
precondición de duplicados son específicas de Postgres/SQL puro, deben probarse con
Testcontainers, no H2 — mismo criterio que `specs/001.../research.md` §9)

**Target Platform**: Linux server (existente, sin cambios)

**Project Type**: Aplicación web única desplegable (existente, Principio IV)

**Performance Goals**: Sin metas nuevas. La verificación de duplicados de CURP corre
una sola vez, en el momento de aplicar la migración (no en tiempo de ejecución de la
API).

**Constraints**: `GET /api/personas` (listado general) no cambia de schema; el nuevo
endpoint de eliminados es una vista separada (spec Clarifications); la restauración de
correo se valida en la misma transacción que la reactivación (FR-009); la migración
DEBE fallar (no aplicarse parcialmente) si existen CURP duplicados entre cualquier
combinación de registros activos/inactivos.

**Scale/Scope**: Mismo volumen que `persona` (padrón); sin nuevas tablas, sin nuevo
volumen de datos más allá de lo que ya existe.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Evaluación |
|---|---|
| I. Respetar lo Existente | PASA. La migración sigue el estilo SQL plano ya usado (`V1`); el `service` sigue el patrón controller→service→repository y reutiliza `GlobalExceptionHandler`/`ApiError`/`DuplicateFieldException`/`RecursoNoEncontradoException` ya existentes, solo agregando códigos de error nuevos. |
| II. No Romper el Contrato | PASA. `POST /api/personas` y `PATCH /api/personas/{id}` no cambian de schema, solo de reglas de validación internas (mismo formato de error 409 ya existente, con un `codigo` nuevo para el caso accionable). `GET /api/personas` no cambia. Los endpoints nuevos (`.../restaurar`, `.../eliminadas`) son aditivos. |
| III. Test-First con Suite Siempre Verde | PASA (gate de proceso). Debe incluirse un test que verifique que la migración falla ante CURP duplicados preexistentes (fixture con datos sembrados antes de aplicar `V4`), además de los tests funcionales de la nueva regla. |
| IV. Privacidad por Diseño | PASA. El 409 accionable de CURP y el 409 de correo al restaurar exponen únicamente un `id` (UUID), nunca nombres, CURP/correo/teléfono del registro referido (FR-004, FR-009; ver `data-model.md`). |
| V. Migraciones Solo Aditivas y Versionadas | PASA. Nueva migración `V4__globalizar_unicidad_curp.sql`, posterior a `V2`/`V3` de `002`/`003`. `V1` no se edita: el índice parcial de CURP se elimina y se reemplaza dentro de `V4`, no modificando el archivo de `V1`. Incluye la verificación de duplicados como precondición explícita y documentada (spec del usuario), con fallo controlado si no se cumple. |
| VI. Identidad vs Contacto | PASA — es la corrección directa del hallazgo que la constitución (v2.0.0) ya había dejado como TODO: CURP pasa a ser identidad vitalicia con unicidad global absoluta, sin excepción por estado activo/inactivo, exactamente como exige el Principio VI. `correo` conserva su unicidad solo-entre-activos (dato de contacto, reutilizable). |
| Restricciones Adicionales | PASA. Sin microservicios, colas ni tablas nuevas. |

Sin violaciones que requieran `Complexity Tracking`.

## Project Structure

### Documentation (this feature)

```text
specs/004-restaurar-persona-curp/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── personas-restaurar-api.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

Mismo proyecto Spring Boot único. Cambios dentro del dominio `persona` existente; sin
paquetes nuevos.

```text
src/main/java/mx/personas/api/
├── common/error/
│   └── ErrorCode.java                    # + PERSONA_CURP_ELIMINADA (409, accionable)
└── persona/
    ├── controller/
    │   └── PersonaController.java        # + POST .../restaurar, + GET /api/personas/eliminadas (ambos @PreAuthorize ADMIN)
    ├── dto/
    │   └── PersonaEliminadaResponseDTO.java  # nuevo (o reutiliza PersonaResponseDTO): shape del listado de eliminados
    ├── repository/
    │   └── PersonaRepository.java        # + findByCurp(String curp) [sin filtro de activo — CURP ahora global]
    │                                       # + findByActivoFalse(Pageable) [vista de eliminados]
    └── service/
        └── PersonaService.java           # crear()/actualizar(): reemplazan el chequeo de CURP por findByCurp + rama activo/inactivo
                                            # + restaurar(id): valida solo correo-contra-activos, reactiva, registra RESTAURACION (vía 003)
                                            # + listarEliminadas(pageable)

src/main/resources/db/migration/
└── V4__globalizar_unicidad_curp.sql      # nueva: precondición de duplicados + DROP índice parcial + ADD CONSTRAINT UNIQUE global
```

**Structure Decision**: Sin cambios de topología ni paquetes nuevos. Todo el trabajo
vive dentro del dominio `persona` ya existente, extendiendo `PersonaService`/
`PersonaController`/`PersonaRepository` y el catálogo de `ErrorCode`.

## Complexity Tracking

*Sin violaciones de la Constitution Check; esta sección no aplica.*

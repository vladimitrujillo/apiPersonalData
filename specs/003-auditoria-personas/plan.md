# Implementation Plan: Auditoría y Historial de Cambios en Personas

**Branch**: `003-auditoria-personas` | **Date**: 2026-07-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-auditoria-personas/spec.md`

## ⚠️ Dependencia bloqueante

Este feature requiere que `002-autenticacion-autorizacion` esté **implementado** (no
solo especificado/planeado): necesita la tabla `usuario`, un `SecurityContext` de Spring
Security poblado por cada request, y method security (`@PreAuthorize`) ya habilitado. A
la fecha de este plan, `002` solo tiene `spec.md`/`plan.md`/artefactos de diseño — el
código (`usuario`, `auth`, `SecurityConfig`) todavía no existe en el repositorio. Las
tareas de este feature (`/speckit-tasks`) no son ejecutables hasta que `002` esté
implementado. Ver también la nota de integración en `research.md` §1 sobre la forma
exacta que debe tener el principal de autenticación para que este feature pueda leer el
`usuario.id` sin una consulta adicional.

## Summary

Añadir auditoría de "quién/cuándo" a `persona` y `direccion` con **Spring Data JPA
Auditing** (`@CreatedBy`/`@CreatedDate`/`@LastModifiedBy`/`@LastModifiedDate` sobre un
`@MappedSuperclass` común, con un `AuditorAware<UUID>` que lee el usuario autenticado del
`SecurityContext`), y un **historial de cambios inmutable** en una tabla propia
(`persona_historial`, con el diff de campos en una columna `JSONB`) poblado
explícitamente desde `PersonaService` en la misma transacción de negocio — en vez de
Hibernate Envers, para controlar el enmascarado de campos sensibles y el formato exacto
del diff (decisión documentada en `research.md`). Se añade también la capacidad de
restaurar una persona eliminada lógicamente (ADMIN, spec Clarifications), como nueva
operación auditada.

## Technical Context

**Language/Version**: Java 21 (existente)

**Primary Dependencies**: Spring Boot 3.3.5 / Spring Data JPA (Auditing — módulo ya
incluido, solo requiere habilitarlo), Hibernate 6.5.x (soporte nativo de `JSONB` vía
`@JdbcTypeCode(SqlTypes.JSON)`, sin dependencia nueva), Jackson (ya presente, para
serializar el diff), Spring Security 6 (de `002`, para `SecurityContext`/`@PreAuthorize`)

**Storage**: PostgreSQL (existente) — 2 columnas nuevas en `persona` y en `direccion`
(`creado_por`, `actualizado_por`), 1 tabla nueva (`persona_historial`)

**Testing**: JUnit 5 + Mockito + Testcontainers/PostgreSQL (existente; `JSONB` y las
nuevas FKs a `usuario` son específicas de Postgres, ver `specs/001.../research.md` §9)

**Target Platform**: Linux server (existente, sin cambios)

**Project Type**: Aplicación web única desplegable (existente, Principio IV)

**Performance Goals**: Sin metas nuevas; el cálculo del diff y el insert de historial
ocurren dentro de la misma transacción de escritura ya existente (sin round-trips
adicionales relevantes).

**Constraints**: Los listados de personas no cambian de schema (FR-004); `GET
/api/personas/{id}` gana campos nuevos de forma aditiva (Principio II); el historial es
de solo lectura (FR-009); ninguna escritura sobre `persona`/`direccion` puede persistir
sin su entrada de historial correspondiente (FR-010, misma transacción).

**Scale/Scope**: Mismo volumen que `persona` (padrón); `persona_historial` crece de
forma acotada (una fila por operación sobre una persona, no por campo).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Evaluación |
|---|---|
| I. Respetar lo Existente | PASA, con una adaptación documentada: `Persona`/`Direccion` pasan a extender un `@MappedSuperclass` (`Auditable`) que absorbe `created_at`/`updated_at` (ya existentes, mismo nombre de columna, sin migración) junto con las dos columnas nuevas `creado_por`/`actualizado_por`. Es la forma consistente de incorporar el mecanismo que el propio solicitante pidió (JPA Auditing) para las cuatro columnas como un solo concepto, en vez de dejar dos mecanismos de timestamp conviviendo (uno manual, uno declarativo) — ver `research.md` §2. El resto (paquetes por dominio, controller→service→repository, MapStruct, `GlobalExceptionHandler`) no cambia de convención. |
| II. No Romper el Contrato | PASA. `GET /api/personas` (listado) no cambia de schema (FR-004). `GET /api/personas/{id}` solo **añade** campos (aditivo). Los nuevos endpoints (`.../historial`, `.../restaurar`) son aditivos. |
| III. Test-First con Suite Siempre Verde | PASA (gate de proceso). `tasks.md` debe ordenar tests en rojo → implementación → suite verde, incluida la suite ya adaptada por `002`. |
| IV. Privacidad por Diseño | PASA, reforzado más allá de logs: CURP/RFC/teléfono se enmascaran **antes** de escribirse en `persona_historial` (research.md §5) — ni siquiera un volcado directo de esa tabla expone el valor en claro. |
| V. Migraciones Solo Aditivas y Versionadas | PASA. Nueva migración `V3__add_auditoria_personas.sql` (aditiva: `ALTER TABLE ... ADD COLUMN` nullable + `CREATE TABLE persona_historial`), aplicada después de `V2` de `002` (dependencia de orden, no de contenido). Ni `V1` ni `V2` se modifican. |
| VI. Identidad vs Contacto | PASA, y se refuerza: `creado_por`/`actualizado_por`/`persona_historial.usuario_id` referencian `usuario(id)`, cuya unicidad de `login` es permanente y nunca se libera (`002`) — la atribución de auditoría sigue siendo válida y resoluble para siempre, incluso si el usuario autor fue desactivado después. |
| Restricciones Adicionales | PASA. Sin microservicios ni colas nuevas. |

Sin violaciones que requieran `Complexity Tracking`.

## Project Structure

### Documentation (this feature)

```text
specs/003-auditoria-personas/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── personas-historial-api.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

Mismo proyecto Spring Boot único. Cambios dentro del dominio `persona` existente, más un
paquete transversal nuevo `common.audit`.

```text
src/main/java/mx/personas/api/
├── common/
│   ├── audit/
│   │   ├── Auditable.java                # nuevo: @MappedSuperclass (creadoPor, createdAt, actualizadoPor, updatedAt)
│   │   ├── SecurityAuditorAware.java      # nuevo: AuditorAware<UUID> leyendo el SecurityContext (ver research.md §1)
│   │   └── MaskingUtil.java               # nuevo: enmascarado de CURP/RFC/teléfono (research.md §5)
│   ├── config/
│   │   └── JpaAuditingConfig.java         # nuevo: @EnableJpaAuditing(auditorAwareRef = "...")
│   └── error/
│       └── ErrorCode.java                  # + PERSONA_YA_ACTIVA (409, restaurar sobre una persona activa)
└── persona/
    ├── controller/
    │   └── PersonaController.java          # + GET .../historial, + PATCH .../restaurar (ambos @PreAuthorize ADMIN)
    ├── dto/
    │   ├── PersonaResponseDTO.java         # + creadoPor/creadoEn/modificadoPor/modificadoEn
    │   ├── DireccionResponseDTO.java       # + mismos 4 campos de auditoría
    │   ├── HistorialEntradaDTO.java        # nuevo: fecha, usuario, operacion, cambios[]
    │   └── HistorialPageResponseDTO.java   # nuevo: mismo shape de paginación que PersonaPageResponseDTO
    ├── mapper/
    │   └── PersonaMapper.java              # + mapeo de campos de auditoría (resuelve login vía UsuarioRepository)
    ├── model/
    │   ├── Persona.java                     # ahora extends Auditable; se retira el manejo manual de created_at/updated_at
    │   ├── Direccion.java                   # idem
    │   └── PersonaHistorial.java            # nuevo: id, persona, usuarioId, operacion, fecha, cambios (JSONB)
    ├── repository/
    │   └── PersonaHistorialRepository.java  # nuevo: findByPersonaIdOrderByFechaDesc(..., Pageable)
    └── service/
        ├── PersonaService.java              # + restaurar(id); calcula diff y persiste PersonaHistorial en cada operación
        └── HistorialDiffService.java        # nuevo: calcula List<CampoCambiado> antes/después, aplica MaskingUtil

src/main/resources/db/migration/
└── V3__add_auditoria_personas.sql          # nueva, aditiva (posterior a V2 de 002)
```

**Structure Decision**: Sin cambios de topología. Todo el código nuevo vive dentro del
dominio `persona` ya existente (más el paquete transversal `common.audit`, al mismo
nivel que `common.security`/`common.error`/`common.config`), siguiendo la convención de
`controller/dto/mapper/model/repository/service` ya establecida.

## Complexity Tracking

*Sin violaciones de la Constitution Check; esta sección no aplica.*

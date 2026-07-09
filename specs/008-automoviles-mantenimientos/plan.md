# Implementation Plan: Automóviles de Personas y Mantenimientos

**Branch**: `008-automoviles-mantenimientos` | **Date**: 2026-07-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-automoviles-mantenimientos/spec.md`

## Summary

Agregar el registro de automóviles pertenecientes a personas y el historial
de mantenimientos de cada automóvil (con piezas cambiadas y mecánico
opcional). Enfoque técnico: mismo stack y convenciones del proyecto
(Spring Boot 3 / JPA / Flyway / MapStruct / springdoc); nuevo dominio
`mx.personas.api.automovil` con dos controllers nuevos
(`AutomovilController`, `MantenimientoController`) y una extensión de
`PersonaController` ya existente; la elegibilidad del mecánico se valida
componiendo los repositorios ya existentes de `007-profesiones-personas`
(sin duplicar queries); la auditoría reutiliza `persona_historial` vía la
persona dueña, extendiendo `HistorialDiffService`, igual que
`persona_profesion` ya hace en 007.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.3.5 (Web, Data JPA, Validation, Security), Flyway, MapStruct, springdoc-openapi

**Storage**: PostgreSQL 16 (índices únicos parciales, `CHECK` constraints)

**Testing**: JUnit 5, Testcontainers (PostgreSQL 16) para tests de repositorio/integración, `@WebMvcTest` + Mockito para controllers

**Target Platform**: Linux server (contenedor Docker), API REST

**Project Type**: Web service (backend único, ya existente)

**Performance Goals**: Sin objetivo nuevo; mismo perfil que el resto de la API (CRUD transaccional de bajo volumen por request)

**Constraints**: Ninguna migración destructiva (Principio V); ningún endpoint existente cambia de contrato (Principio II); auditoría "quién/cuándo/qué cambió" obligatoria (FR-028)

**Scale/Scope**: 3 entidades nuevas (`Automovil`, `Mantenimiento`, `PiezaCambiada`), 2 controllers nuevos, 1 controller extendido, ~11 códigos de error nuevos

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Evaluación |
|---|---|
| I. Respetar lo Existente | PASS — estructura de paquetes por dominio, controller→service→repository, MapStruct, `GlobalExceptionHandler`, migraciones `V{n}__`; ver research.md para cada decisión de reutilización explícita (§1-2, §4, §8, §12) |
| II. No Romper el Contrato | PASS — todos los endpoints son nuevos (`/api/personas/{id}/automoviles`, `/api/automoviles/**`, `/api/mantenimientos/**`); ningún endpoint existente cambia de forma o código de estado |
| III. Test-First con Suite Siempre Verde | PASS — `tasks.md` incluye tests de repositorio (Testcontainers) y de integración por historia de usuario antes/junto con cada implementación; FR-029/SC-009 exigen la suite completa en verde al final |
| IV. Privacidad por Diseño | PASS — el mecánico se expone solo como `{id, nombreCompleto}` (research.md §9), nunca la entidad `Persona` completa; ningún dato personal nuevo se loguea |
| V. Migraciones Solo Aditivas y Versionadas | PASS — una única migración nueva `V8__` con 3 tablas nuevas, sin tocar V1-V7 |
| VI. Identidad vs Contacto | PASS — VIN modelado como identidad vitalicia (unicidad global, inmutable, análogo a CURP); placas como dato reasignable (unicidad solo entre activos, análogo a correo) — research.md §1 |

**Resultado**: Sin violaciones. No se requiere `Complexity Tracking`.

## Project Structure

### Documentation (this feature)

```text
specs/008-automoviles-mantenimientos/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md         # Phase 1 output
├── quickstart.md         # Phase 1 output
├── contracts/
│   └── automoviles-mantenimientos-api.md
└── tasks.md              # Phase 2 output (/speckit-tasks — NOT created by /speckit-plan)
```

### Source Code (repository root)

Mismo proyecto Spring Boot único. Nuevo dominio `automovil`; el alta/listado
de automóviles de una persona vive en el `PersonaController` ya existente
(mismo criterio que `007-profesiones-personas` con `/personas/{id}/profesiones`).

```text
src/main/java/mx/personas/api/
├── common/error/
│   └── ErrorCode.java                            # + 11 códigos nuevos (ver contracts/automoviles-mantenimientos-api.md)
├── persona/
│   ├── controller/
│   │   └── PersonaController.java                # MODIFICADO: + POST/GET /{id}/automoviles
│   └── service/
│       └── HistorialDiffService.java             # MODIFICADO: + serialización de alta/edición/baja/restauración de automóvil y mantenimiento (persona_historial)
└── automovil/
    ├── controller/
    │   ├── AutomovilController.java               # nuevo: GET/PATCH/DELETE /{id}, POST /{id}/restaurar, POST/GET /{id}/mantenimientos
    │   └── MantenimientoController.java           # nuevo: GET/PATCH/DELETE /{id}, POST /{id}/restaurar
    ├── dto/
    │   ├── AutomovilRequestDTO.java                # nuevo: alta
    │   ├── AutomovilUpdateDTO.java                  # nuevo: edición parcial (marca/modelo/anio/color/placas)
    │   ├── AutomovilResponseDTO.java                 # nuevo
    │   ├── MantenimientoRequestDTO.java                # nuevo: alta (incluye piezas embebidas)
    │   ├── MantenimientoUpdateDTO.java                  # nuevo: edición parcial (incluye piezas embebidas — reemplazo completo)
    │   ├── MantenimientoResponseDTO.java                 # nuevo: incluye piezas y MecanicoResumenDTO
    │   ├── PiezaCambiadaDTO.java                           # nuevo: request/response de una pieza
    │   ├── MecanicoResumenDTO.java                           # nuevo: {id, nombreCompleto} — proyección, nunca Persona completa
    │   └── MantenimientoPageResponseDTO.java                  # nuevo: paginación (mismo shape que el resto de listados)
    ├── model/
    │   ├── Automovil.java                            # nuevo: extends Auditable
    │   ├── Mantenimiento.java                          # nuevo: extends Auditable; @OneToMany piezas (cascade=ALL, orphanRemoval=true)
    │   └── PiezaCambiada.java                            # nuevo: @ManyToOne a Mantenimiento; NO extends Auditable
    ├── repository/
    │   ├── AutomovilRepository.java                    # nuevo
    │   └── MantenimientoRepository.java                  # nuevo
    ├── mapper/
    │   ├── AutomovilMapper.java                            # nuevo: MapStruct
    │   └── MantenimientoMapper.java                          # nuevo: MapStruct (incluye resolución de MecanicoResumenDTO)
    └── service/
        ├── AutomovilService.java                              # nuevo: crear/editar/eliminar/restaurar/listar-por-persona/obtener
        └── MantenimientoService.java                            # nuevo: registrar-con-piezas/editar/eliminar/restaurar/listar-historial/obtener; valida elegibilidad de mecánico componiendo repositorios de `profesion` (research.md §2)

src/main/resources/db/migration/
└── V8__create_automovil_mantenimiento_pieza.sql   # nueva, aditiva
```

**Structure Decision**: Nuevo dominio `automovil` (paralelo a `profesion`,
`codigopostal`, `usuario`), ya que `Automovil` y `Mantenimiento` son
recursos de primera clase con su propio ciclo de vida y permisos (research.md
§8). El alta/listado de automóviles de una persona se agrega al
`PersonaController` ya existente, replicando el patrón de
`007-profesiones-personas`; a diferencia de esa feature (donde el
sub-recurso `PersonaProfesion` no justificaba un controller propio por tener
solo una operación adicional — retirar), aquí `Automovil` y `Mantenimiento`
sí tienen suficientes operaciones propias (CRUD completo + restaurar cada
uno) para justificar sus propios controllers. La elegibilidad del mecánico
vive en `MantenimientoService` (dominio `automovil`), que depende de
`ProfesionRepository`/`PersonaProfesionRepository` (dominio `profesion`) —
nunca al revés, evitando una dependencia circular entre dominios.

## Complexity Tracking

*Sin violaciones de la Constitution Check; sección no aplica.*

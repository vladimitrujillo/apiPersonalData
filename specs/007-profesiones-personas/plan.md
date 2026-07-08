# Implementation Plan: Catálogo de Profesiones y Asignación a Personas

**Branch**: `007-profesiones-personas` | **Date**: 2026-07-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-profesiones-personas/spec.md`

## Summary

Agregar un catálogo administrado de profesiones (nombre único insensible a
mayúsculas/acentos, descripción opcional, activo/desactivada, semilla
"Mecánico") y la capacidad de asignar una o varias profesiones del catálogo a
una persona, con fecha desde y cédula/certificado opcionales. Una persona no
puede tener la misma profesión asignada *activa* dos veces, pero sí puede
acumular episodios históricos (retirar y volver a asignar crea una fila
nueva, nunca reactiva la anterior — decisión de `/speckit-clarify`). Se
agrega un directorio paginado de personas por profesión, expuesto con un DTO
reducido (id, nombre completo, fecha desde, cédula) que nunca filtra el resto
de los datos personales. Gestión del catálogo restringida a ADMIN; asignar y
retirar permitido a ADMIN y CAPTURISTA; todas las consultas permitidas a
ambos roles. Dos tablas nuevas (aditivas): `profesion` y `persona_profesion`.

## Technical Context

**Language/Version**: Java 21 (existente)

**Primary Dependencies**: Spring Boot 3.3.5 (Web, Data JPA, Validation,
existente), MapStruct (existente), springdoc-openapi (existente), Spring
Security 6 (`@PreAuthorize` ADMIN/CAPTURISTA, de `002`), extensión PostgreSQL
`unaccent` (ya creada por `005-busqueda-avanzada-personas`, `V5`)

**Storage**: PostgreSQL (existente) — 2 tablas nuevas: `profesion` (catálogo)
y `persona_profesion` (asignación); ambas vía migración Flyway aditiva `V7`

**Testing**: JUnit 5 + Mockito + Testcontainers/PostgreSQL (existente; la
unicidad insensible a acentos usa `unaccent`, que no existe en H2 — mismo
criterio que `005-busqueda-avanzada-personas`)

**Target Platform**: Linux server (existente)

**Project Type**: Aplicación web única desplegable (existente)

**Performance Goals**: Sin metas nuevas específicas; el directorio por
profesión y el listado del catálogo son consultas paginadas de volumen bajo
(catálogo de profesiones: decenas/cientos de filas; directorio: subconjunto
de personas del padrón), sin requisitos de rendimiento distintos de los
listados paginados ya existentes (`GET /api/personas`, `GET
/api/codigos-postales/importaciones`).

**Constraints**: Ningún endpoint existente cambia de schema (todos los
endpoints de esta feature son nuevos); la unicidad de `profesion.nombre` y de
`persona_profesion` (activa) se hacen cumplir tanto en el service (mensaje de
negocio con código de error específico) como con una restricción de base de
datos (respaldo ante condiciones de carrera), replicando el patrón ya usado
para `persona.correo`/`persona.curp`.

**Scale/Scope**: Catálogo de profesiones pequeño (decenas/cientos de
entradas, administrado manualmente por ADMIN); `persona_profesion` crece con
el padrón y acumula historial (una fila por episodio de asignación,
incluyendo retiradas), volumen proporcional al de `persona`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Evaluación |
|---|---|
| I. Respetar lo Existente | PASA. Nuevo dominio `mx.personas.api.profesion` sigue el patrón `controller/dto/mapper/model/repository/service` ya usado en `codigopostal`/`usuario`; los sub-recursos `/api/personas/{id}/profesiones` viven en el `PersonaController` ya existente, igual que `historial`/`restaurar`; los endpoints de acción (`desactivar`, `reactivar`, `retirar`) siguen el patrón `PATCH .../accion` ya usado en `UsuarioController`; el formato de error reutiliza `GlobalExceptionHandler`. |
| II. No Romper el Contrato | PASA. Todos los endpoints de esta feature son nuevos (aditivos); ningún endpoint existente (`/api/personas`, `/api/codigos-postales/**`, etc.) cambia de schema ni de comportamiento. |
| III. Test-First con Suite Siempre Verde | PASA (gate de proceso). Debe incluir tests para: unicidad de nombre insensible a mayúsculas/acentos (Testcontainers), unicidad de asignación activa (índice parcial), bloqueo de asignación sobre profesión desactivada, bloqueo de asignación sobre persona eliminada, reasignación tras retiro (crea episodio nuevo, no reactiva), exclusión del directorio para personas eliminadas/asignaciones retiradas, y permisos por rol (403 a CAPTURISTA en gestión de catálogo). |
| IV. Privacidad por Diseño | PASA. La cédula/certificado es un dato semi-sensible; el directorio por profesión expone deliberadamente un DTO reducido (id, nombre completo, fecha desde, cédula) que nunca incluye correo/teléfono/CURP/RFC/dirección — reduce la superficie de exposición de datos personales respecto a devolver la persona completa. |
| V. Migraciones Solo Aditivas y Versionadas | PASA. Nueva migración `V7__create_profesion_persona_profesion.sql` (aditiva), posterior a `V6` de `006-sepomex-import-automatico`. Ninguna migración previa se edita. |
| VI. Identidad vs Contacto | PASA — no aplica de forma directa (la profesión no es identidad ni dato de contacto de la persona); la cédula/certificado no se usa como credencial ni se le exige unicidad global. |
| Restricciones Adicionales (Simplicidad) | PASA. Mismo artefacto desplegable, sin microservicios ni colas nuevas; el catálogo de profesiones es administrado por ADMIN (no un import masivo), consistente con la simplicidad de un CRUD acotado. |

Sin violaciones que requieran `Complexity Tracking`.

## Project Structure

### Documentation (this feature)

```text
specs/007-profesiones-personas/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── profesiones-api.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

Mismo proyecto Spring Boot único. Nuevo dominio `profesion`; tres nuevos
endpoints sub-recurso viven en el `PersonaController` ya existente.

```text
src/main/java/mx/personas/api/
├── common/error/
│   └── ErrorCode.java                          # + 7 códigos nuevos (ver contracts/profesiones-api.md)
├── persona/
│   ├── controller/
│   │   └── PersonaController.java              # MODIFICADO: + POST/GET /{id}/profesiones, PATCH /{id}/profesiones/{asignacionId}/retirar
│   └── service/
│       └── HistorialDiffService.java           # MODIFICADO: + serialización de asignar/retirar profesión (persona_historial)
└── profesion/
    ├── controller/
    │   └── ProfesionController.java             # nuevo: catálogo (POST, GET, PATCH descripción, PATCH desactivar/reactivar) + directorio (GET /{id}/personas)
    ├── dto/
    │   ├── ProfesionRequestDTO.java              # nuevo: alta (nombre, descripción opcional)
    │   ├── ProfesionUpdateDTO.java                # nuevo: edición (descripción opcional)
    │   ├── ProfesionResponseDTO.java               # nuevo
    │   ├── AsignacionProfesionRequestDTO.java       # nuevo: asignar (profesionId, fechaDesde opcional, cédula opcional)
    │   ├── AsignacionProfesionResponseDTO.java       # nuevo
    │   ├── PersonaDirectorioDTO.java                  # nuevo: id, nombreCompleto, fechaDesde, cédula — SIN el resto de datos personales
    │   └── *PageResponseDTO.java                       # nuevo: paginación (mismo shape que el resto de listados)
    ├── model/
    │   ├── Profesion.java                          # nuevo: extends Auditable
    │   └── PersonaProfesion.java                    # nuevo: extends Auditable
    ├── repository/
    │   ├── ProfesionRepository.java                 # nuevo
    │   └── PersonaProfesionRepository.java           # nuevo
    ├── mapper/
    │   └── ProfesionMapper.java                      # nuevo: MapStruct
    └── service/
        ├── ProfesionService.java                      # nuevo: catálogo (crear/editar/desactivar/reactivar/listar) + directorio
        └── PersonaProfesionService.java                 # nuevo: asignar/retirar/listar-por-persona (usado desde PersonaController)

src/main/resources/db/migration/
└── V7__create_profesion_persona_profesion.sql   # nueva, aditiva
```

**Structure Decision**: Nuevo dominio `profesion` (paralelo a `codigopostal`/
`usuario`) para el catálogo y el directorio, ya que son recursos de primera
clase con su propia gestión administrativa. Los 3 endpoints de asignación
que cuelgan de `/api/personas/{id}/...` se agregan al `PersonaController` ya
existente — igual que `historial` y `restaurar` — en vez de crear un
controller separado, para no fragmentar el sub-recurso "profesiones de una
persona" fuera de su propio dominio padre. La lógica de negocio de esa
asignación (validar persona activa, profesión activa, no-duplicado activo,
crear episodio nuevo al reasignar) vive en `PersonaProfesionService` dentro
del dominio `profesion` (por depender fuertemente de `Profesion`), inyectado
en `PersonaController`.

## Complexity Tracking

*Sin violaciones de la Constitution Check; esta sección no aplica.*

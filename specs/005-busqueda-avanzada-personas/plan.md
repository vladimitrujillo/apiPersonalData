# Implementation Plan: Búsqueda Avanzada de Personas

**Branch**: `005-busqueda-avanzada-personas` | **Date**: 2026-07-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-busqueda-avanzada-personas/spec.md`

## Summary

Extender `GET /api/personas` (mismo endpoint, mismo contrato de respuesta) con
criterios de búsqueda combinables (texto insensible a acentos, CURP parcial, rango de
edad, rango de fecha de registro, sexo, estado activo/eliminado solo-ADMIN) y
ordenamiento, todos opcionales y combinados con AND. Enfoque técnico: `Specification`
(Criteria API) de Spring Data JPA compuesta dinámicamente por criterio presente,
reemplazando la actual query JPQL fija de 3 parámetros; insensibilidad a acentos vía
extensión PostgreSQL `unaccent` envuelta en una función `IMMUTABLE` (necesaria para
poder indexarla); rango de edad traducido a límites de fecha de nacimiento calculados en
el servicio (nunca una función por fila en SQL, para no invalidar índices).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.3.5, Spring Data JPA (`JpaSpecificationExecutor`
+ Criteria API), Spring Security 6 (rol ya autenticado), Bean Validation (Hibernate
Validator, ya en el proyecto), Flyway

**Storage**: PostgreSQL (extensión `unaccent` nueva, ver research.md §4)

**Testing**: JUnit 5 + Mockito + AssertJ (unit/`@WebMvcTest`); Testcontainers/PostgreSQL
para los tests de repositorio/Specification — `unaccent` no existe en H2 y este
proyecto no usa H2, ya usa Testcontainers exclusivamente para IT

**Target Platform**: Linux server (sin cambio respecto al proyecto existente)

**Project Type**: Web service Spring Boot único (sin cambio de estructura)

**Performance Goals**: Sin objetivo numérico explícito en el spec. Dirección explícita
del usuario: empezar con un índice funcional simple (btree sobre la expresión
`unaccent_immutable`) y medir antes de considerar `pg_trgm`/GIN.

**Constraints**: `GET /api/personas` no puede cambiar de path, método, ni de schema de
respuesta (FR-012/FR-013); ningún parámetro existente (`nombre`, `municipio`, `estado`,
`page`, `size`) cambia de nombre ni de semántica.

**Scale/Scope**: No especificado numéricamente en el spec; mismo volumen de datos que el
sistema ya maneja hoy (catálogo SEPOMEX + registros de `persona`/`direccion`).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Evaluación |
|---|---|
| I. Respetar lo Existente | PASA. Mismas capas (controller → service → repository), mismo formato de error (`GlobalExceptionHandler`), reutiliza `PersonaPageResponseDTO`/`PersonaResumenDTO` sin crear DTOs de respuesta nuevos. `buscarActivas` (JPQL) se retira y se reemplaza por una única `Specification` — se documenta explícitamente en research.md §7 por qué esto es consolidar, no duplicar, un patrón. |
| II. No Romper el Contrato | PASA. Todos los parámetros nuevos son opcionales y aditivos; los 3 existentes no cambian de nombre/semántica (research.md §2 documenta por qué el nuevo criterio de estado activo/eliminado se llama `estadoRegistro`, no `estado`, precisamente para no colisionar con/alterar el `estado` geográfico ya existente). FR-013 exige explícitamente respuesta idéntica sin parámetros nuevos. |
| III. Test-First con Suite Siempre Verde | Aplica igual que en features anteriores; `tasks.md` antepone tests. La suite existente (`PersonaControllerListTest`, `PersonaServiceTest`) debe seguir en verde con las mismas aserciones, ahora respaldada por una Specification en vez de JPQL. |
| IV. Privacidad por Diseño | PASA. `curpPrefijo` es un dato sensible parcial; se valida/sanitiza como cualquier otro parámetro de entrada (misma capa de Bean Validation), sin loguearse. No se introduce ningún log nuevo. |
| V. Migraciones Solo Aditivas y Versionadas | PASA. Nueva migración `V5__unaccent_busqueda_personas.sql`: crea extensión, función `IMMUTABLE` e índice funcional; no toca `V1`-`V4`. |
| VI. Identidad vs Contacto | PASA sin cambios. El criterio `estadoRegistro` solo *filtra* por `persona.activo`, no modifica la unicidad de CURP/correo ni el modelo de identidad de `004`. |

## Project Structure

### Documentation (this feature)

```text
specs/005-busqueda-avanzada-personas/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md         # Phase 1 output
├── quickstart.md         # Phase 1 output
├── contracts/
│   └── personas-busqueda-api.md
└── tasks.md              # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

Proyecto único Maven/Spring Boot (sin cambio de estructura):

```text
src/main/resources/db/migration/
└── V5__unaccent_busqueda_personas.sql          # nuevo

src/main/java/mx/personas/api/persona/
├── dto/
│   └── PersonaBusquedaFiltroDTO.java             # nuevo (record + Bean Validation)
├── repository/
│   ├── PersonaRepository.java                    # modificado: se retira buscarActivas(...)
│   └── PersonaSpecifications.java                # nuevo (factory de Specification<Persona>)
├── service/
│   └── PersonaService.java                       # modificado: listar(...) usa la Specification;
│                                                  #   valida rangos/orden; calcula límites de edad
└── controller/
    └── PersonaController.java                     # modificado: nuevos @RequestParam en listar(...);
                                                    #   resuelve ADMIN vs CAPTURISTA para estadoRegistro

src/test/java/mx/personas/api/persona/
├── PersonaControllerListTest.java                 # extendido (nuevos params, sin romper aserciones existentes)
├── PersonaServiceTest.java                        # extendido (validaciones, cálculo de límites de edad)
└── PersonaSpecificationsTest.java                 # nuevo (unit, sin PostgreSQL — solo construcción de predicados)

src/test/java/mx/personas/api/integration/
└── PersonaBusquedaIT.java                          # nuevo (Testcontainers — unaccent, subquery municipio/estado,
                                                     #   rango de edad, estadoRegistro por rol, orden)
```

**Structure Decision**: Se mantiene la estructura por dominio ya existente
(`mx.personas.api.persona.{controller,dto,mapper,model,repository,service}`). No se crea
ningún paquete ni módulo nuevo; el único archivo nuevo fuera de dto/repository/test es la
migración Flyway.

## Complexity Tracking

*Sin violaciones de la Constitution Check que requieran justificación.*

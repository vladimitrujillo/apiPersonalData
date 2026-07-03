# Implementation Plan: Gestión de Personas y Catálogo de Códigos Postales

**Branch**: `001-personas-codigos-postales` | **Date**: 2026-07-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-personas-codigos-postales/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

API REST para administrar personas (datos personales, dirección y estado activo/eliminado
lógico) y para consultar el catálogo nacional de códigos postales de SEPOMEX, con
integración de dirección que valida y autocompleta contra dicho catálogo. Se implementa
como una única aplicación Spring Boot 3.x en capas (controller → service → repository),
persistida en PostgreSQL vía Spring Data JPA, con migraciones Flyway, mapeo DTO↔entidad
con MapStruct, validación Bean Validation, documentación OpenAPI vía springdoc, y manejo
global de errores con `@ControllerAdvice` en un formato JSON consistente.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.x — Spring Web (REST), Spring Data JPA (persistencia),
Bean Validation / Jakarta Validation (validación de entrada), springdoc-openapi (documentación
OpenAPI/Swagger), Flyway (migraciones de esquema), MapStruct (mapeo entidad↔DTO)

**Storage**: PostgreSQL (H2 en memoria y/o Testcontainers con PostgreSQL para pruebas)

**Testing**: JUnit 5, Mockito, Spring Boot Test (`@WebMvcTest`, `@DataJpaTest`), Testcontainers
para pruebas de integración contra PostgreSQL real

**Target Platform**: Servidor Linux (contenedor Docker), desplegable como aplicación única
(JAR ejecutable Spring Boot)

**Project Type**: Web service (proyecto único — no hay frontend en este feature)

**Performance Goals**: Búsqueda de colonias por nombre parcial (autocompletado) con p95 <
300ms; operaciones CRUD de persona y consulta exacta de CP con p95 < 200ms, en línea con
SC-004 y SC-007 del spec

**Constraints**: Aplicación única sin microservicios (Principio IV de la constitución);
ningún dato personal en texto plano en logs (Principio III); todas las respuestas de error
siguen un formato JSON consistente (Principio V); el catálogo SEPOMEX se importa de forma
idempotente (Principio VI); todo endpoint requiere autenticación (FR-023)

**Scale/Scope**: Catálogo SEPOMEX del orden de ~100k códigos postales y ~150k colonias a
nivel nacional; volumen de personas esperado bajo-medio (miles a decenas de miles de
registros), con lecturas más frecuentes que escrituras

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Evaluación | Cómo se cumple |
|---|---|---|
| I. API-First | ✅ PASS | Todos los endpoints se documentan con springdoc-openapi (OpenAPI generado desde anotaciones); contratos definidos en `contracts/` antes de implementar |
| II. Test-First (NON-NEGOTIABLE) | ✅ PASS | JUnit 5 + Mockito + Testcontainers; tasks.md exigirá tests (contrato/integración/unitarios) escritos antes de la implementación de cada historia |
| III. Privacidad por Diseño | ✅ PASS (con diseño explícito) | Bean Validation sanitiza/valida en la capa de entrada (DTOs); logging configurado para excluir cuerpos de petición/respuesta con datos personales (ver research.md); manejador global de errores enmascara valores sensibles |
| IV. Simplicidad | ✅ PASS | Una sola aplicación Spring Boot, arquitectura en capas simple (controller/service/repository), sin microservicios ni colas |
| V. Consistencia en Manejo de Errores | ✅ PASS | `@ControllerAdvice` centralizado produce un formato JSON único (código + mensaje) documentado en OpenAPI y en `contracts/error-format.md` |
| VI. Catálogos de Referencia Locales e Idempotentes | ✅ PASS | Catálogo SEPOMEX cargado vía proceso de importación (job) que hace upsert idempotente sobre tablas Flyway-versionadas; test de idempotencia planeado |

No se identifican violaciones. No aplica la tabla de Complexity Tracking.

**Re-evaluación post-diseño (tras Fase 1)**: `research.md` y `data-model.md` confirman
decisiones concretas para cada principio (borrado lógico explícito, índice único parcial,
importación idempotente por upsert, validación Bean Validation en la capa de entrada,
formato de error único, filtro de API key) sin introducir componentes adicionales
(microservicios, colas, frameworks extra). El gate sigue en ✅ PASS sin violaciones.

## Project Structure

### Documentation (this feature)

```text
specs/001-personas-codigos-postales/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── personas-api.md
│   ├── codigos-postales-api.md
│   └── error-format.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
# Opción 1: Proyecto único (Maven, Spring Boot) — arquitectura en capas
src/main/java/mx/personas/api/
├── persona/
│   ├── controller/         # PersonaController
│   ├── service/             # PersonaService (+ impl)
│   ├── repository/          # PersonaRepository, DireccionRepository (Spring Data JPA)
│   ├── model/                # Entidades Persona, Direccion (tabla propia, FK persona_id)
│   ├── dto/                  # PersonaRequestDTO, PersonaResponseDTO, PersonaUpdateDTO, DireccionDTO
│   └── mapper/               # PersonaMapper, DireccionMapper (MapStruct)
├── codigopostal/
│   ├── controller/         # CodigoPostalController
│   ├── service/             # CodigoPostalService
│   ├── repository/          # CpCatalogoRepository (tabla plana cp_catalogo)
│   ├── model/                # Entidad CpCatalogo
│   ├── dto/                  # CodigoPostalResponseDTO, ColoniaDTO
│   ├── mapper/               # CodigoPostalMapper
│   └── importer/             # SepomexImportService (upsert idempotente ON CONFLICT (codigo_postal, id_asenta_cpcons))
├── common/
│   ├── error/                 # ApiError DTO, GlobalExceptionHandler (@ControllerAdvice)
│   ├── config/                 # OpenApiConfig, CacheConfig, SecurityConfig (API key)
│   └── security/                # ApiKeyAuthFilter
└── ApiPersonalDataApplication.java

src/main/resources/
├── db/migration/            # Scripts Flyway V1__..., V2__... (esquema + seed opcional)
└── application.yml

src/test/java/mx/personas/api/
├── persona/                 # Tests unitarios (service), @WebMvcTest (controller), @DataJpaTest (repository)
├── codigopostal/             # Tests unitarios, contrato, importador idempotente
├── integration/               # Tests de extremo a extremo con Testcontainers (PostgreSQL)
└── common/                    # Tests del manejador global de errores
```

**Structure Decision**: Proyecto único Maven/Spring Boot con arquitectura en capas
(controller → service → repository) y DTOs separados de las entidades, organizado por
módulo de dominio (`persona`, `codigopostal`, `common`), conforme al Principio IV
(Simplicidad) de la constitución — sin separación en múltiples proyectos ni microservicios.

## Complexity Tracking

*No aplica: el Constitution Check no reporta violaciones.*

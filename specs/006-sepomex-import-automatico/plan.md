# Implementation Plan: Automatización de la Actualización del Catálogo SEPOMEX

**Branch**: `006-sepomex-import-automatico` | **Date**: 2026-07-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-sepomex-import-automatico/spec.md`

## ⚠️ Dependencia

Este feature requiere `002-autenticacion-autorizacion` implementado: la tabla `usuario`
(FK desde la bitácora para el origen manual) y `@PreAuthorize` ADMIN para los endpoints
nuevos. A la fecha de este plan, `002` solo está especificado/planeado.

## Summary

Orquestar el importador SEPOMEX ya existente (`SepomexImportService`) con: un job
`@Scheduled` (cron configurable, default semanal) que revisa un directorio y procesa
archivos no vistos antes; un endpoint de subida manual (`MultipartFile`, solo ADMIN)
que valida tamaño y estructura antes de importar; una tabla `catalogo_importacion`
(bitácora, Flyway aditiva) que registra cada corrida; y una invalidación del caché de
CP al finalizar con éxito. La concurrencia se controla con un **advisory lock de
PostgreSQL de alcance de transacción** (no en memoria), para que el candado siga siendo
válido si la aplicación llega a desplegarse en más de una instancia. El importador
existente se extiende mínimamente (ver `research.md` §1-2): su forma de reportar
resultados y su tolerancia a filas inválidas cambian; su lógica de upsert (la clave
natural `codigo_postal + id_asenta_cpcons` y el propio `ON CONFLICT`) no cambia.

## Technical Context

**Language/Version**: Java 21 (existente)

**Primary Dependencies**: Spring Boot 3.3.5 (`@EnableScheduling`/`@Scheduled`, ya
incluido en `spring-context`, sin dependencia nueva), Spring Data JPA (existente),
Flyway (existente), Spring Cache (existente, `CacheManager`/`@CacheEvict`), Spring
Security 6 (de `002`, `@PreAuthorize` ADMIN)

**Storage**: PostgreSQL (existente) — 1 tabla nueva (`catalogo_importacion`); el
candado de ejecución única usa `pg_try_advisory_xact_lock` (nativo de Postgres, sin
tabla ni estructura adicional)

**Testing**: JUnit 5 + Mockito + Testcontainers/PostgreSQL (existente; el advisory lock
y el `ON CONFLICT ... RETURNING` son específicos de Postgres — Testcontainers, no H2,
mismo criterio que `specs/001.../research.md` §9)

**Target Platform**: Linux server (existente); diseño compatible con más de una
instancia de la aplicación corriendo a la vez (candado en BD, no en memoria)

**Project Type**: Aplicación web única desplegable, potencialmente replicada
horizontalmente (Principio IV — ver Constitution Check)

**Performance Goals**: Las consultas de CP existentes no deben degradarse durante una
importación (FR-013) — el `@Cacheable` existente sigue sirviendo desde caché hasta que
se invalida al finalizar con éxito.

**Constraints**: Ningún endpoint existente cambia de schema; el candado es de BD, no de
memoria (spec/decisión del usuario); la lógica de upsert de `CpCatalogoRepository` no
cambia de semántica, solo se extiende para reportar su resultado (research.md §2).

**Scale/Scope**: Un archivo de catálogo por corrida (~150k filas, ver
`specs/001.../research.md` §3b); la bitácora crece una fila por corrida (programada o
manual), volumen bajo.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Evaluación |
|---|---|
| I. Respetar lo Existente | PASA. `SepomexImportService` se extiende mínimamente (mismo método `importar(Path)`, mismo paquete `codigopostal.importer`), no se reescribe su lógica de upsert; los endpoints nuevos siguen el patrón `controller/dto/mapper/model/repository/service` ya usado en el dominio `codigopostal`; la migración sigue el estilo SQL plano de `V1`. |
| II. No Romper el Contrato | PASA. `GET /api/codigos-postales/{cp}` y `GET /api/colonias` no cambian de schema ni de disponibilidad durante una importación (FR-013). Los endpoints nuevos (subida manual, bitácora) son aditivos. |
| III. Test-First con Suite Siempre Verde | PASA (gate de proceso). Debe incluir tests para: candado de concurrencia (dos disparos simultáneos, Testcontainers), archivo estructuralmente inválido (catálogo intacto), fila individual inválida (contada como rechazada sin abortar), idempotencia visible en el resumen (subir el mismo archivo dos veces). |
| IV. Privacidad por Diseño | PASA — no aplica de forma directa (el catálogo de CP no es dato personal); la bitácora referencia `usuario_id` (identidad de operador, no dato de persona del padrón). |
| V. Migraciones Solo Aditivas y Versionadas | PASA. Nueva migración `V6__create_catalogo_importacion.sql` (aditiva), posterior a las de `002`-`005` (`V5` ya la ocupó `005-busqueda-avanzada-personas` con `V5__unaccent_busqueda_personas.sql`). Ninguna migración previa se edita. |
| VI. Identidad vs Contacto | PASA. `catalogo_importacion.usuario_id` referencia `usuario(id)` (identidad permanente de `002`); sin impacto en el modelo de `persona`. |
| Restricciones Adicionales (Simplicidad) | PASA, con una precisión: el candado en BD (en vez de en memoria) es explícitamente para que la aplicación pueda escalar a más de una instancia sin duplicar importaciones — esto no introduce microservicios ni colas (sigue siendo el mismo artefacto desplegable, solo potencialmente replicado), por lo que no viola el Principio de Simplicidad; es la opción más simple que sigue siendo correcta bajo réplicas. |

Sin violaciones que requieran `Complexity Tracking`.

## Project Structure

### Documentation (this feature)

```text
specs/006-sepomex-import-automatico/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── catalogo-importacion-api.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

Mismo proyecto Spring Boot único. Todo el trabajo nuevo vive dentro del dominio
`codigopostal` ya existente.

```text
src/main/java/mx/personas/api/
├── common/error/
│   └── ErrorCode.java                        # + CATALOGO_ARCHIVO_INVALIDO (400), CATALOGO_ARCHIVO_DEMASIADO_GRANDE (400), CATALOGO_IMPORTACION_EN_CURSO (409)
└── codigopostal/
    ├── controller/
    │   └── CatalogoImportacionController.java   # nuevo: POST .../importaciones (multipart, ADMIN), GET .../importaciones (bitácora, ADMIN)
    ├── dto/
    │   ├── ResumenImportacionDTO.java            # nuevo: insertados/actualizados/sinCambio/rechazados
    │   ├── CorridaImportacionDTO.java             # nuevo: fila de bitácora
    │   └── CorridaImportacionPageResponseDTO.java # nuevo: paginación (mismo shape que PersonaPageResponseDTO)
    ├── model/
    │   └── CatalogoImportacion.java               # nuevo: entidad de la bitácora
    ├── repository/
    │   ├── CpCatalogoRepository.java              # upsert(): + RETURNING para distinguir insertado/actualizado/sin cambio (research.md §2)
    │   ├── CatalogoImportacionRepository.java     # nuevo
    │   └── AdvisoryLockRepository.java             # nuevo: pg_try_advisory_xact_lock (research.md §3)
    ├── service/
    │   ├── CatalogoImportacionOrchestrator.java   # nuevo: candado + validación estructural + llama a SepomexImportService + bitácora + evicción de caché
    │   └── CodigoPostalService.java                # sin cambios de lógica; se evict()a desde el orchestrator, no desde aquí
    └── importer/
        ├── SepomexImportService.java              # MODIFICADO mínimamente (research.md §1): importar() ahora devuelve ResumenImportacion; valida estructura antes de upsertear; tolera filas individualmente inválidas
        ├── SepomexImportScheduler.java             # nuevo: @Scheduled(cron = "${app.sepomex.import-cron}")
        └── SepomexImportRunner.java                # sin cambios de comportamiento; se adapta al nuevo tipo de retorno de importar()

src/main/resources/db/migration/
└── V6__create_catalogo_importacion.sql        # nueva, aditiva
```

**Structure Decision**: Sin paquetes nuevos fuera de `codigopostal`; se añade
`CatalogoImportacionOrchestrator` como la única pieza nueva de orquestación (candado +
validación + bitácora + caché), dejando `SepomexImportService` enfocado únicamente en
parsear el archivo y upsertear filas — la extensión mínima que pide el usuario.

## Complexity Tracking

*Sin violaciones de la Constitution Check; esta sección no aplica.*

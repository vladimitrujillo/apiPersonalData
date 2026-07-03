---

description: "Task list for Gestión de Personas y Catálogo de Códigos Postales"
---

# Tasks: Gestión de Personas y Catálogo de Códigos Postales

**Input**: Design documents from `/specs/001-personas-codigos-postales/`
**Prerequisites**: plan.md, spec.md, data-model.md, research.md, contracts/, quickstart.md

**Tests**: INCLUDED. El Principio II de la constitución (`.specify/memory/constitution.md`)
es Test-First y NON-NEGOTIABLE — todas las historias de usuario incluyen tareas de test que
deben escribirse y fallar antes de la implementación correspondiente.

**Organization**: Tareas agrupadas por historia de usuario (spec.md) para permitir
implementación y prueba independiente de cada una.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: Historia de usuario a la que pertenece (US1..US5)
- Cada tarea incluye la ruta de archivo exacta

## Path Conventions

Proyecto único Maven/Spring Boot (ver `plan.md` → Project Structure):

- `src/main/java/mx/personas/api/...`
- `src/main/resources/db/migration/...`
- `src/test/java/mx/personas/api/...`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Inicialización del proyecto Maven/Spring Boot

- [X] T001 Crear proyecto Maven (Spring Initializr o manual) con Java 21 y Spring Boot 3.x en `pom.xml`, con estructura de paquetes `src/main/java/mx/personas/api/` y `src/test/java/mx/personas/api/` per `plan.md`
- [X] T002 Agregar dependencias a `pom.xml`: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `flyway-core`, `flyway-database-postgresql`, `postgresql` (driver), `mapstruct` + `mapstruct-processor`, `springdoc-openapi-starter-webmvc-ui`, `spring-boot-starter-cache`, `spring-boot-starter-test`, `org.testcontainers:junit-jupiter`, `org.testcontainers:postgresql`
- [X] T003 [P] Configurar `src/main/resources/application.yml` (datasource PostgreSQL, `spring.jpa.hibernate.ddl-auto=validate`, `spring.flyway.enabled=true`, `springdoc.api-docs.path`, `spring.mvc.log-request-details=false` per research.md §8)
- [X] T004 [P] Crear `docker-compose.yml` en la raíz del repo con servicio `db` (PostgreSQL) para desarrollo local, referenciado por `quickstart.md`
- [X] T005 [P] Crear clase base de pruebas de integración `AbstractIntegrationTest` en `src/test/java/mx/personas/api/common/AbstractIntegrationTest.java` que levanta un contenedor Testcontainers de PostgreSQL compartido (research.md §9)
- [X] T006 [P] Configurar plugins Maven de build/test (`maven-compiler-plugin` con `--release 21`, `maven-surefire-plugin`, `maven-failsafe-plugin` para separar tests unitarios de integración) en `pom.xml`

**Checkpoint**: Proyecto compila (`./mvnw compile`) y el contenedor de pruebas puede levantarse.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Esquema de base de datos y componentes transversales que TODAS las historias
necesitan (Constitution Principios III, V, VI)

**⚠️ CRITICAL**: Ninguna historia de usuario puede comenzar hasta completar esta fase

- [X] T007 Crear migración Flyway `src/main/resources/db/migration/V1__create_schema.sql` con las tablas `persona` (incluye `activo BOOLEAN NOT NULL DEFAULT true`), `direccion` (FK `persona_id`, FK opcional `cp_catalogo_id`), y `cp_catalogo` (columna `id_asenta_cpcons`), más: índices únicos parciales `ux_persona_correo_activo`/`ux_persona_curp_activo` `WHERE activo = true`, `UNIQUE (codigo_postal, id_asenta_cpcons)` en `cp_catalogo`, `ix_cp_catalogo_codigo_postal`, e `ix_cp_catalogo_estado_municipio_asenta` — per `data-model.md`
- [X] T008 [P] Crear DTO `ApiError` (`codigo`, `mensaje`, `detalles[]`) en `src/main/java/mx/personas/api/common/error/ApiError.java` per `contracts/error-format.md`
- [X] T009 [P] Crear excepciones de dominio `RecursoNoEncontradoException`, `DuplicateFieldException`, `ColoniaInvalidaException`, `FormatoInvalidoException` en `src/main/java/mx/personas/api/common/error/`
- [X] T010 Implementar `GlobalExceptionHandler` (`@RestControllerAdvice`) en `src/main/java/mx/personas/api/common/error/GlobalExceptionHandler.java` mapeando `MethodArgumentNotValidException`/`ConstraintViolationException` → 400 `VALIDACION_FALLIDA`, y las excepciones de dominio de T009 a los códigos de `contracts/error-format.md` (depende de T008, T009)
- [X] T011 [P] Implementar `ApiKeyAuthFilter` (`OncePerRequestFilter`) en `src/main/java/mx/personas/api/common/security/ApiKeyAuthFilter.java` que valida el header `X-API-Key`, y `SecurityConfig` que lo registra excluyendo rutas de Swagger/OpenAPI (research.md §6, FR-023)
- [X] T012 [P] Configurar `OpenApiConfig` (springdoc) en `src/main/java/mx/personas/api/common/config/OpenApiConfig.java`
- [X] T013 [P] Configurar `CacheConfig` (`@EnableCaching`, `ConcurrentMapCache` para el caché del catálogo) en `src/main/java/mx/personas/api/common/config/CacheConfig.java` (research.md §7)
- [X] T014 [P] Crear clase principal `ApiPersonalDataApplication` en `src/main/java/mx/personas/api/ApiPersonalDataApplication.java`

**Checkpoint**: Esquema aplicado vía Flyway, manejo de errores y autenticación listos —
las historias de usuario pueden comenzar.

---

## Phase 3: User Story 1 - Ciclo de vida de una persona (Priority: P1) 🎯 MVP

**Goal**: Crear, consultar, actualizar parcialmente y eliminar lógicamente una persona.

**Independent Test**: Crear una persona con datos válidos, consultarla por ID, actualizar
un campo, eliminarla, y verificar que la consulta posterior responde 404 mientras el
registro sigue en la base de datos con `activo = false`.

### Tests for User Story 1 ⚠️

> Escribir estos tests PRIMERO y verificar que fallan antes de implementar (Principio II)

- [X] T015 [P] [US1] Test de contrato `POST /api/personas` (creación válida, 400 por formato inválido, 400 por fecha futura, 409 por correo/CURP duplicado) en `src/test/java/mx/personas/api/persona/PersonaControllerCreateTest.java` (`@WebMvcTest`)
- [X] T016 [P] [US1] Test de contrato `GET /api/personas/{id}` (200 si activa, 404 si no existe o eliminada) en `src/test/java/mx/personas/api/persona/PersonaControllerGetTest.java`
- [X] T017 [P] [US1] Test de contrato `PATCH /api/personas/{id}` (actualización parcial preserva campos no enviados, 404 si eliminada) en `src/test/java/mx/personas/api/persona/PersonaControllerUpdateTest.java`
- [X] T018 [P] [US1] Test de contrato `DELETE /api/personas/{id}` (204 en éxito, 404 si ya eliminada) en `src/test/java/mx/personas/api/persona/PersonaControllerDeleteTest.java`
- [X] T019 [P] [US1] Test de integración del ciclo completo crear→consultar→actualizar→eliminar con Testcontainers en `src/test/java/mx/personas/api/integration/PersonaLifecycleIT.java` (extiende `AbstractIntegrationTest`; verifica SC-001 y SC-003)
- [X] T020 [P] [US1] Test de repositorio verificando que el índice único parcial permite reutilizar correo/CURP de una persona eliminada lógicamente, en `src/test/java/mx/personas/api/persona/PersonaRepositoryIT.java` (extiende `AbstractIntegrationTest`)

### Implementation for User Story 1

- [X] T021 [P] [US1] Crear entidad `Persona` (campos spec + `activo`) en `src/main/java/mx/personas/api/persona/model/Persona.java`
- [X] T022 [P] [US1] Crear entidad `Direccion` (FK `persona_id`, FK opcional `cpCatalogoId`) en `src/main/java/mx/personas/api/persona/model/Direccion.java`
- [X] T023 [US1] Crear `PersonaRepository` (`findByIdAndActivoTrue`, `existsByCorreoAndActivoTrue`, `existsByCurpAndActivoTrue`) en `src/main/java/mx/personas/api/persona/repository/PersonaRepository.java` (depende de T021)
- [X] T024 [US1] Crear `DireccionRepository` en `src/main/java/mx/personas/api/persona/repository/DireccionRepository.java` (depende de T022)
- [X] T025 [P] [US1] Crear DTOs `PersonaRequestDTO`, `PersonaUpdateDTO`, `PersonaResponseDTO`, `DireccionDTO` con anotaciones Bean Validation (CURP/RFC/teléfono/correo/CP, `@PastOrPresent` en fecha de nacimiento) en `src/main/java/mx/personas/api/persona/dto/` per research.md §4
- [X] T026 [US1] Crear `PersonaMapper` y `DireccionMapper` (MapStruct) en `src/main/java/mx/personas/api/persona/mapper/` (depende de T025)
- [X] T027 [US1] Implementar `PersonaService` (crear, obtener por ID, actualizar parcial, eliminar lógicamente; valida duplicados de correo/CURP contra activos y fecha no futura, lanza excepciones de T009) en `src/main/java/mx/personas/api/persona/service/PersonaService.java` (depende de T023, T024, T026)
- [X] T028 [US1] Implementar `PersonaController` (`POST /api/personas`, `GET /api/personas/{id}`, `PATCH /api/personas/{id}`, `DELETE /api/personas/{id}`) en `src/main/java/mx/personas/api/persona/controller/PersonaController.java` (depende de T027)

**Checkpoint**: US1 completamente funcional y probable de forma independiente — MVP listo.

---

## Phase 4: User Story 2 - Listado y filtrado de personas (Priority: P2)

**Goal**: Listar personas activas con paginación y filtros por nombre, municipio y estado.

**Independent Test**: Con varias personas creadas (incluidas algunas eliminadas
lógicamente), solicitar listados con distintas combinaciones de filtros/páginas y
verificar que las eliminadas no aparecen y que una página fuera de rango regresa vacío.

### Tests for User Story 2 ⚠️

- [X] T029 [P] [US2] Test de contrato `GET /api/personas` (sin filtros, con filtros de nombre/municipio/estado, página fuera de rango) en `src/test/java/mx/personas/api/persona/PersonaControllerListTest.java`
- [X] T030 [P] [US2] Test de integración confirmando que personas eliminadas lógicamente no aparecen en ningún listado, con Testcontainers, en `src/test/java/mx/personas/api/integration/PersonaListIT.java`

### Implementation for User Story 2

- [X] T031 [US2] Agregar consultas paginadas/filtradas (`Specification` o métodos derivados: por nombre parcial, municipio, estado, `activo = true`) a `PersonaRepository` en `src/main/java/mx/personas/api/persona/repository/PersonaRepository.java` (depende de T023)
- [X] T032 [US2] Extender `PersonaService` con `listarPersonas(filtros, pageable)` en `src/main/java/mx/personas/api/persona/service/PersonaService.java` (depende de T031)
- [X] T033 [US2] Agregar `GET /api/personas` a `PersonaController` y DTO `PersonaPageResponseDTO` (`contenido`, `pagina`, `tamanoPagina`, `totalElementos`, `totalPaginas`) en `src/main/java/mx/personas/api/persona/controller/PersonaController.java` y `src/main/java/mx/personas/api/persona/dto/PersonaPageResponseDTO.java` (depende de T032)

**Checkpoint**: US1 y US2 funcionan juntas de forma independiente.

---

## Phase 5: User Story 3 - Consulta de catálogo de código postal exacto (Priority: P2)

**Goal**: Consultar un CP de 5 dígitos y obtener estado, municipio y colonias; catálogo
importado de forma idempotente desde SEPOMEX.

**Independent Test**: Con el catálogo importado, consultar un CP conocido y verificar
estado/municipio/colonias; consultar un CP inexistente (404) y uno mal formado (400).
Ejecutar el importador dos veces con el mismo archivo y verificar que el conteo de filas
no cambia (SC-005).

### Tests for User Story 3 ⚠️

- [X] T034 [P] [US3] Test de contrato `GET /api/codigos-postales/{cp}` (200 con colonias, 404 `CP_NO_ENCONTRADO`, 400 `CP_FORMATO_INVALIDO`) en `src/test/java/mx/personas/api/codigopostal/CodigoPostalControllerTest.java`
- [X] T035 [P] [US3] Test de integración de consulta de CP con datos sembrados en PostgreSQL real (Testcontainers) en `src/test/java/mx/personas/api/integration/CodigoPostalIT.java`
- [X] T036 [P] [US3] Test de idempotencia del importador: ejecutar `SepomexImportService` dos veces con el mismo archivo fuente y verificar que el conteo de filas de `cp_catalogo` no cambia, y que una fila modificada en una "nueva versión" del archivo se actualiza sin duplicarse, en `src/test/java/mx/personas/api/codigopostal/SepomexImportServiceIT.java` (extiende `AbstractIntegrationTest`)

### Implementation for User Story 3

- [X] T037 [P] [US3] Crear entidad `CpCatalogo` (tabla plana: `codigoPostal`, `estado`, `municipio`, `asentamiento`, `tipoAsentamiento`, `idAsentaCpcons`) en `src/main/java/mx/personas/api/codigopostal/model/CpCatalogo.java`
- [X] T038 [US3] Crear `CpCatalogoRepository` (`findByCodigoPostal`) en `src/main/java/mx/personas/api/codigopostal/repository/CpCatalogoRepository.java` (depende de T037)
- [X] T039 [P] [US3] Crear DTOs `CodigoPostalResponseDTO`, `ColoniaDTO` en `src/main/java/mx/personas/api/codigopostal/dto/`
- [X] T040 [US3] Implementar `CodigoPostalService.consultarPorCodigoPostal(cp)` (agrupa filas de `CpCatalogo` por CP, lanza `RecursoNoEncontradoException` si no existe, anotado `@Cacheable`) en `src/main/java/mx/personas/api/codigopostal/service/CodigoPostalService.java` (depende de T038, T039)
- [X] T041 [US3] Implementar `CodigoPostalController` con `GET /api/codigos-postales/{cp}` (valida formato 5 dígitos → 400 antes de llamar al service) en `src/main/java/mx/personas/api/codigopostal/controller/CodigoPostalController.java` (depende de T040)
- [X] T042 [US3] Implementar `SepomexImportService` (lee el archivo fuente del catálogo y ejecuta upsert `INSERT ... ON CONFLICT (codigo_postal, id_asenta_cpcons) DO UPDATE`) en `src/main/java/mx/personas/api/codigopostal/importer/SepomexImportService.java` (depende de T038; research.md §3)
- [X] T043 [US3] Implementar `SepomexImportRunner` (`CommandLineRunner` activado por argumento `--import-sepomex=<ruta>`, per `quickstart.md`) en `src/main/java/mx/personas/api/codigopostal/importer/SepomexImportRunner.java` (depende de T042)

**Checkpoint**: Catálogo de códigos postales consultable e importable de forma
independiente de Personas.

---

## Phase 6: User Story 4 - Autocompletado de colonias por nombre parcial (Priority: P3)

**Goal**: Buscar colonias por coincidencia parcial de nombre, opcionalmente acotado a
estado o municipio.

**Independent Test**: Con el catálogo importado, buscar un fragmento de nombre de colonia
con y sin acotar por estado/municipio, y verificar que una búsqueda sin coincidencias
regresa lista vacía sin error.

### Tests for User Story 4 ⚠️

- [X] T044 [P] [US4] Test de contrato `GET /api/colonias` (coincidencia parcial, filtro por estado/municipio, sin resultados → 200 vacío, sin `nombre` → 400) en `src/test/java/mx/personas/api/codigopostal/ColoniaControllerTest.java`

### Implementation for User Story 4

- [X] T045 [US4] Agregar consulta `buscarPorNombreParcial(nombre, estado, municipio)` (usa el índice `(estado, municipio, asentamiento)`) a `CpCatalogoRepository` en `src/main/java/mx/personas/api/codigopostal/repository/CpCatalogoRepository.java` (depende de T038)
- [X] T046 [US4] Extender `CodigoPostalService` con `buscarColonias(nombre, estado, municipio)` en `src/main/java/mx/personas/api/codigopostal/service/CodigoPostalService.java` (depende de T045)
- [X] T047 [US4] Agregar `GET /api/colonias` a `CodigoPostalController` en `src/main/java/mx/personas/api/codigopostal/controller/CodigoPostalController.java` (depende de T046)

**Checkpoint**: Autocompletado de colonias funcional de forma independiente.

---

## Phase 7: User Story 5 - Dirección de persona validada contra el catálogo de CP (Priority: P3)

**Goal**: Al registrar/actualizar la dirección de una persona con CP mexicano, validar su
existencia, autocompletar municipio/estado, y validar que la colonia pertenezca al CP.

**Independent Test**: Con una persona existente y el catálogo importado, registrar/
actualizar su dirección con un CP válido (verifica autocompletado), un CP inexistente
(verifica error) y una colonia fuera de la lista del CP (verifica error).

### Tests for User Story 5 ⚠️

- [X] T048 [P] [US5] Test de integración: crear/actualizar persona con dirección MX y CP válido autocompleta municipio/estado y acepta colonia de la lista, con Testcontainers, en `src/test/java/mx/personas/api/integration/PersonaDireccionIT.java`
- [X] T049 [P] [US5] Test de integración: CP inexistente (`404 CP_NO_ENCONTRADO`), colonia no perteneciente al CP (`400 COLONIA_NO_VALIDA_PARA_CP`), y dirección con país distinto de MX acepta CP como texto libre sin validar, en `src/test/java/mx/personas/api/integration/PersonaDireccionValidationIT.java`

### Implementation for User Story 5

- [X] T050 [US5] Implementar `DireccionValidationService` (si `pais = MX`: valida CP contra `CpCatalogoRepository`, autocompleta municipio/estado, valida que la colonia esté entre los asentamientos del CP; si `pais != MX`: no valida) en `src/main/java/mx/personas/api/persona/service/DireccionValidationService.java` (depende de T038, T040)
- [X] T051 [US5] Integrar `DireccionValidationService` en el flujo de creación/actualización de `PersonaService` (depende de T027, T050)

**Checkpoint**: Todas las historias de usuario funcionan de forma independiente y en
conjunto.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Mejoras que afectan a varias historias

- [X] T052 [P] Test unitario del `GlobalExceptionHandler` cubriendo cada código de `contracts/error-format.md` en `src/test/java/mx/personas/api/common/error/GlobalExceptionHandlerTest.java`
- [X] T053 [P] Test que verifica que ningún log generado durante creación/actualización de una persona contiene CURP, RFC, correo o teléfono en texto plano (Principio III) en `src/test/java/mx/personas/api/common/PiiLoggingIT.java` (renombrado de `PiiLoggingTest.java` porque extiende `AbstractIntegrationTest` y requiere Docker; debe correr vía failsafe, no surefire)
- [X] T054 [P] Revisar y completar anotaciones OpenAPI (`@Operation`, `@ApiResponse`, `@Tag`) en `PersonaController`, `CodigoPostalController` y `ColoniaController` para que springdoc genere documentación completa (Principio I)
- [X] T055 [P] Agregar `Cache-Control` a las respuestas de `CodigoPostalController` (FR-018) en `src/main/java/mx/personas/api/codigopostal/controller/CodigoPostalController.java`
- [X] T056 Ejecutar manualmente la validación de `quickstart.md` de extremo a extremo contra el ambiente local (docker-compose + importación + los 7 pasos del quickstart) y documentar el resultado

  **Resultado (2026-07-02)**: Ejecutado contra la app empaquetada (`mvn package`) corriendo
  sobre PostgreSQL real de `docker-compose`. Todos los pasos verificados con éxito:
  - Import SEPOMEX (3 filas) + reimport idempotente (conteo se mantiene en 3) — SC-005 ✓
  - `GET /api/codigos-postales/06700` → 200 con colonias; `00000` → 404; `ABCDE` → 400 ✓
  - `GET /api/colonias?nombre=roma&estado=...` → autocompletado correcto ✓
  - Ciclo completo crear→consultar→actualizar→eliminar; autocompletado de
    municipio/estado desde el catálogo (US5) ✓; tras eliminar, `GET` → 404 pero
    `SELECT ... FROM persona` confirma `activo=f` con el registro intacto — SC-001/SC-003 ✓
  - Reutilización de correo de una persona ya eliminada lógicamente confirmada
    (comportamiento esperado); duplicado contra persona *activa* → 409
    `PERSONA_CORREO_DUPLICADO` ✓; fecha futura → 400 `FECHA_NACIMIENTO_FUTURA` ✓;
    CP mexicano inexistente → 404 `CP_NO_ENCONTRADO` ✓; colonia fuera de lista → 400
    `COLONIA_NO_VALIDA_PARA_CP` (mensaje incluye colonias válidas) ✓
  - `GET /api/personas?municipio=...` excluye correctamente a la persona eliminada ✓
  - Sin header `X-API-Key` → 401 ✓; `GET /v3/api-docs` accesible sin auth, expone los 3
    tags (Personas, Códigos Postales, Colonias) y las 4 rutas ✓
  - `Cache-Control: max-age=86400` presente en la respuesta de códigos postales ✓

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Sin dependencias — puede iniciar de inmediato
- **Foundational (Phase 2)**: Depende de Setup — BLOQUEA todas las historias de usuario
- **User Stories (Phase 3-7)**: Todas dependen de Foundational
  - US1 (P1) y US3 (P2) pueden avanzar en paralelo entre sí (no comparten archivos de
    dominio: `persona/*` vs `codigopostal/*`)
  - US2 (P2) depende de que existan `Persona`/`PersonaRepository` (T021, T023 de US1)
  - US4 (P3) depende de que exista `CpCatalogoRepository` (T038 de US3)
  - US5 (P3) depende de US1 (`PersonaService`, T027) y de US3 (`CpCatalogoRepository`/
    `CodigoPostalService`, T038, T040)
- **Polish (Phase 8)**: Depende de que las historias deseadas estén completas

### User Story Dependencies

- **US1 (P1)**: Ninguna — solo Foundational
- **US2 (P2)**: Depende de entidades/repositorio de US1 (T021, T023)
- **US3 (P2)**: Ninguna — solo Foundational (independiente de Personas)
- **US4 (P3)**: Depende del repositorio de catálogo de US3 (T038)
- **US5 (P3)**: Depende de US1 (servicio de personas) y de US3 (repositorio/servicio de catálogo)

### Within Each User Story

- Tests escritos y en rojo antes de la implementación (Principio II, NON-NEGOTIABLE)
- Entidades antes de repositorios; repositorios antes de servicios; servicios antes de
  controladores
- Historia completa y verificada antes de pasar a la siguiente en orden de prioridad

### Parallel Opportunities

- Todas las tareas [P] de Setup (T003-T006) en paralelo
- Todas las tareas [P] de Foundational (T008, T009, T011-T014) en paralelo tras T007
- Dentro de US1: todos los tests T015-T020 en paralelo; T021/T022 en paralelo
- US1 y US3 pueden desarrollarse en paralelo por equipos distintos tras Foundational
  (no comparten archivos)
- Dentro de US3: T034-T036 (tests) en paralelo; T037 y T039 en paralelo

---

## Parallel Example: User Story 1

```bash
# Lanzar todos los tests de US1 en paralelo:
Task: "Contract test POST /api/personas en src/test/java/mx/personas/api/persona/PersonaControllerCreateTest.java"
Task: "Contract test GET /api/personas/{id} en src/test/java/mx/personas/api/persona/PersonaControllerGetTest.java"
Task: "Contract test PATCH /api/personas/{id} en src/test/java/mx/personas/api/persona/PersonaControllerUpdateTest.java"
Task: "Contract test DELETE /api/personas/{id} en src/test/java/mx/personas/api/persona/PersonaControllerDeleteTest.java"
Task: "Integration test ciclo completo en src/test/java/mx/personas/api/integration/PersonaLifecycleIT.java"
Task: "Repository test unicidad condicional en src/test/java/mx/personas/api/persona/PersonaRepositoryIT.java"

# Lanzar las entidades de US1 en paralelo:
Task: "Crear entidad Persona en src/main/java/mx/personas/api/persona/model/Persona.java"
Task: "Crear entidad Direccion en src/main/java/mx/personas/api/persona/model/Direccion.java"
```

---

## Implementation Strategy

### MVP First (Solo User Story 1)

1. Completar Fase 1: Setup
2. Completar Fase 2: Foundational (CRÍTICO — bloquea todas las historias)
3. Completar Fase 3: User Story 1
4. **DETENER y VALIDAR**: probar el ciclo completo de Persona de forma independiente
   (quickstart.md paso 5)
5. Desplegar/demostrar si está listo (MVP)

### Incremental Delivery

1. Setup + Foundational → base lista
2. US1 → probar de forma independiente → MVP
3. US2 → probar de forma independiente (listados/filtros)
4. US3 → probar de forma independiente (catálogo de CP, puede desarrollarse en paralelo a US1/US2)
5. US4 → probar de forma independiente (autocompletado de colonias)
6. US5 → probar de forma independiente (integración dirección↔catálogo)
7. Polish

### Parallel Team Strategy

Con múltiples desarrolladores, tras completar Foundational:

- Desarrollador A: US1 → luego US2 (dependiente)
- Desarrollador B: US3 → luego US4 (dependiente)
- Una vez US1 y US3 completas: cualquiera puede tomar US5 (depende de ambas)

---

## Notes

- [P] = archivos distintos, sin dependencias pendientes
- [Story] mapea cada tarea a su historia de usuario para trazabilidad
- Tests escritos primero y verificados en rojo antes de implementar (Principio II)
- Cada historia debe quedar completa y probable de forma independiente antes de continuar
- Hacer commit tras cada tarea o grupo lógico de tareas
- Evitar: tareas vagas, conflictos de mismo archivo entre tareas [P], dependencias entre
  historias que rompan su independencia

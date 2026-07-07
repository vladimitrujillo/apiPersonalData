---

description: "Task list for Búsqueda Avanzada de Personas"
---

# Tasks: Búsqueda Avanzada de Personas

**Input**: Design documents from `/specs/005-busqueda-avanzada-personas/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED. El Principio III de la constitución (`.specify/memory/constitution.md`)
es Test-First con Suite Siempre Verde y NON-NEGOTIABLE.

**⚠️ Nota de arquitectura**: `buscarActivas` (JPQL de 3 parámetros) se retira por completo
en la Fase Foundational y se reemplaza por una única `Specification<Persona>` compuesta
incrementalmente por cada historia de usuario (research.md §7). Esto significa que
`PersonaSpecifications.java`, `PersonaService.java` y `PersonaController.java` se tocan en
más de una fase — es un crecimiento incremental del mismo archivo, no una duplicación.

**Organization**: Tareas agrupadas por historia de usuario (spec.md) para permitir
implementación y prueba independiente de cada una, salvo la dependencia estructural
señalada arriba.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: Historia de usuario a la que pertenece (US1..US3)
- Cada tarea incluye la ruta de archivo exacta

## Path Conventions

Proyecto único Maven/Spring Boot (ver `plan.md` → Project Structure):

- `src/main/java/mx/personas/api/...`
- `src/main/resources/db/migration/...`
- `src/test/java/mx/personas/api/...`

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: Reemplazar `buscarActivas` (JPQL) por una `Specification<Persona>` con
comportamiento **idéntico** al actual (mismos 3 criterios: nombre/municipio/estado, sin
`unaccent` todavía), de forma que el refactor en sí no sea observable — FR-013 se cumple
antes de agregar ninguna capacidad nueva.

**⚠️ CRITICAL**: Ninguna historia de usuario puede implementarse hasta completar esta fase

- [X] T001 Migración `src/main/resources/db/migration/V5__unaccent_busqueda_personas.sql`:
      `CREATE EXTENSION IF NOT EXISTS unaccent`; función wrapper
      `unaccent_immutable(text) RETURNS text ... LANGUAGE sql IMMUTABLE PARALLEL SAFE
      STRICT` que llama a `unaccent('unaccent', $1)`; índice funcional
      `idx_persona_nombre_completo_unaccent` sobre
      `LOWER(unaccent_immutable(nombres || ' ' || apellidos))` (research.md §4; posterior
      a `V4` de `004`, no se toca `V1`-`V4`)
- [X] T002 [P] Nuevo `src/main/java/mx/personas/api/persona/repository/PersonaSpecifications.java`:
      métodos estáticos `Specification<Persona>` — `conActivoTrue()`,
      `conNombreParcial(String nombre)` (LIKE simple, **sin** `unaccent` todavía — se
      actualiza en US1), `conMunicipio(String municipio)` y `conEstadoGeografico(String
      estado)` (ambos vía subquery correlacionada sobre `Direccion`, replicando la
      semántica `EXISTS` exacta que usa hoy `buscarActivas` — research.md §6); todos
      devuelven `null`/predicado neutro cuando el criterio es `null` o vacío
- [X] T003 Modificar `src/main/java/mx/personas/api/persona/repository/PersonaRepository.java`:
      **quitar** `buscarActivas(...)` — `JpaSpecificationExecutor<Persona>` ya extendido
      hoy provee `findAll(Specification, Pageable)`, no se necesita ningún método nuevo
      (research.md §7)
- [X] T004 Modificar `src/main/java/mx/personas/api/persona/service/PersonaService.java`:
      reescribir `listar(nombre, municipio, estado, pageable)` para componer
      `Specification.allOf(conActivoTrue(), conNombreParcial(nombre),
      conMunicipio(municipio), conEstadoGeografico(estado))` y llamar
      `personaRepository.findAll(spec, pageable)` en vez de `buscarActivas(...)` — misma
      firma pública, mismo `PersonaPageResponseDTO`/`PersonaResumenDTO` de salida
- [X] T005 [P] Nuevo integration test
      `src/test/java/mx/personas/api/integration/PersonaBusquedaIT.java`: casos de
      regresión — filtrar por `nombre` (coincidencia parcial, sin acentos en el texto de
      búsqueda todavía), por `municipio`, por `estado` geográfico, cada uno confirmando
      exactamente los mismos resultados que producía `buscarActivas` hoy (no existe hoy
      un IT que ejercite el `EXISTS` de municipio/estado contra una base real — este es
      el que lo fija como comportamiento verificado antes del refactor)
- [X] T006 [P] Confirmar que `PersonaServiceTest` y `PersonaControllerListTest`
      (existentes) siguen en verde **sin modificar sus aserciones** — validan que el
      cambio de JPQL a `Specification` no altera el comportamiento observable (FR-013)

**Checkpoint**: `listar(...)` se comporta exactamente igual que hoy, ahora sobre
`Specification`. Ninguna historia de usuario tiene aún su comportamiento nuevo
implementado.

---

## Phase 2: User Story 1 - Encontrar personas por texto aunque no se escriban acentos (Priority: P1) 🎯 MVP

**Goal**: `nombre` (ya existente) ignora acentos/diacríticos además de mayúsculas.

**Independent Test**: Con "José García" registrado, buscar "jose" y "garcia" por
separado y confirmar que ambas lo encuentran; confirmar que sin parámetros nuevos la
respuesta es idéntica a la de hoy.

### Tests for User Story 1 ⚠️

- [X] T007 [US1] Extender `PersonaBusquedaIT`: crear persona "José García", `GET
      /api/personas?nombre=jose` y `?nombre=garcia` (por separado) → ambas la
      encuentran (Acceptance Scenarios 1-2)
- [X] T008 [P] [US1] Extender `PersonaControllerListTest`: `GET /api/personas` sin
      parámetros nuevos de este feature → mismo shape/comportamiento que antes
      (Acceptance Scenario 3; FR-011, FR-013 — confirma explícitamente que no se
      aplica ningún `Sort` cuando `ordenarPor` está ausente; si ya existe un test
      equivalente, solo confirmar que cubre este caso explícitamente)

### Implementation for User Story 1

- [X] T009 [US1] Modificar `conNombreParcial(...)` en `PersonaSpecifications.java`:
      envolver ambos lados de la comparación con
      `criteriaBuilder.function("unaccent_immutable", String.class, ...)` +
      `criteriaBuilder.lower(...)` (research.md §4)

**Checkpoint**: US1 verificable de forma independiente.

---

## Phase 3: User Story 2 - Combinar varios criterios a la vez (Priority: P1)

**Goal**: Agregar `curpPrefijo`, rango de edad, rango de fecha de registro, `sexo` y
ordenamiento, todos opcionales y combinados con AND junto a los criterios existentes.

**Independent Test**: Combinar texto + rango de edad + estado geográfico en una sola
llamada y confirmar que el resultado es la intersección exacta de los tres.

### Tests for User Story 2 ⚠️

- [X] T010 [P] [US2] Extender `PersonaBusquedaIT`: combinar texto + `edadMinima`/
      `edadMaxima` + `estado` geográfico → resultado es la intersección exacta, no
      unión (Acceptance Scenario 1)
- [X] T011 [P] [US2] Extender `PersonaBusquedaIT`: combinar `curpPrefijo` +
      `fechaRegistroDesde`/`Hasta` + `municipio` + `sexo` → intersección exacta
      (Acceptance Scenario 2); incluir un caso de `curpPrefijo` sin coincidencias →
      página vacía sin error (Edge Cases)
- [X] T012 [P] [US2] Extender `PersonaBusquedaIT`: `ordenarPor=FECHA_NACIMIENTO` y
      `ordenarPor=FECHA_REGISTRO`, ambas direcciones (`ASC`/`DESC`) → resultado
      respeta el orden (Acceptance Scenario 3); un caso de dos personas nacidas el
      mismo día exacto en el límite de un rango de edad (research.md §5, verificar
      que no hay off-by-one en el borde del cumpleaños)
- [X] T013 [P] [US2] Extender `PersonaControllerListTest`: `edadMinima=-1` → 400
      `VALIDACION_FALLIDA` con `campo=edadMinima` (Bean Validation real, sin mockear
      el service); `ordenarPor=APELLIDO` (valor no reconocido) → 400 con
      `campo=ordenarPor` (service mockeado lanzando `FormatoInvalidoException`)
      (FR-014, FR-016)
- [X] T014 [P] [US2] Nuevo/extendido `PersonaServiceTest`: método de cálculo de
      límites de fecha de nacimiento a partir de `edadMinima`/`edadMaxima` — casos
      borde exactos del cumpleaños (research.md §5: nacido exactamente en el límite
      incluido, nacido un día antes/después excluido en cada dirección);
      `edadMinima > edadMaxima` → `FormatoInvalidoException` con `campo=edadMaxima`;
      `fechaRegistroDesde > fechaRegistroHasta` → `campo=fechaRegistroHasta`;
      `ordenarPor`/`direccionOrden` fuera de whitelist → `campo` correspondiente
      (FR-014, FR-015, FR-016)

### Implementation for User Story 2

- [X] T015 [US2] Nuevo
      `src/main/java/mx/personas/api/persona/dto/PersonaBusquedaFiltroDTO.java`
      (record): `nombre, municipio, estado, curpPrefijo,
      @Min(0) Integer edadMinima, @Min(0) Integer edadMaxima, LocalDate
      fechaRegistroDesde, LocalDate fechaRegistroHasta, String sexo, String
      ordenarPor, String direccionOrden` (data-model.md; **`estadoRegistro` se agrega
      en T021/US3, no aquí** — mantiene el campo cuyo efecto depende del rol aislado
      en la historia que lo introduce)
- [X] T016 [US2] Agregar a `PersonaSpecifications.java`: `conCurpPrefijo(String
      prefijo)` (LIKE `prefijo%`, sin `unaccent`), `conFechaNacimientoEntre(LocalDate
      desde, LocalDate hasta)`, `conFechaRegistroEntre(LocalDate desde, LocalDate
      hasta)` (sobre `Auditable.createdAt`), `conSexo(String sexo)` (igualdad exacta)
- [X] T017 [US2] Modificar `PersonaService.java`: nuevo método
      `listar(PersonaBusquedaFiltroDTO filtro, Pageable pageable)`; calcula los
      límites de fecha de nacimiento desde `edadMinima`/`edadMaxima` (research.md §5,
      fórmulas exactas ya verificadas); valida imperativamente `edadMinima >
      edadMaxima`, `fechaRegistroDesde > fechaRegistroHasta` y la whitelist de
      `ordenarPor`/`direccionOrden`, lanzando `FormatoInvalidoException(ErrorCode.
      VALIDACION_FALLIDA, "<parametro>", "...")` en cada caso; compone
      `Specification.allOf(...)` con todos los predicados presentes; aplica `Sort`
      según `ordenarPor`/`direccionOrden` o ninguno si se omite (FR-011); **retirar el
      overload `listar(nombre, municipio, estado, pageable)` introducido en T004 —
      queda superado por este método y no debe quedar como código muerto/paralelo**
- [X] T018 [US2] Modificar `PersonaController.java`: agregar `@RequestParam`
      opcionales `curpPrefijo, edadMinima, edadMaxima, fechaRegistroDesde,
      fechaRegistroHasta, sexo, ordenarPor, direccionOrden` a `listar(...)`, bind a
      `@Valid @ModelAttribute PersonaBusquedaFiltroDTO`, delegar al nuevo método de
      `PersonaService`

**Checkpoint**: US2 combina todos los criterios nuevos; US1 sigue funcionando.

---

## Phase 4: User Story 3 - Un CAPTURISTA nunca ve personas eliminadas por esta vía (Priority: P1)

**Goal**: `estadoRegistro` (activo/eliminado) solo tiene efecto para ADMIN; para
CAPTURISTA se ignora y se fuerza `ACTIVAS`, sin error.

**Independent Test**: Con una persona eliminada, CAPTURISTA buscando con
`estadoRegistro=ELIMINADAS` no la ve; ADMIN con el mismo parámetro sí la ve.

### Tests for User Story 3 ⚠️

- [X] T019 [P] [US3] Extender `PersonaBusquedaIT`: CAPTURISTA con
      `estadoRegistro=ELIMINADAS` (y también con `TODAS`) → la persona eliminada NO
      aparece, mismo resultado que sin el parámetro (Acceptance Scenarios 1-2); ADMIN
      con `estadoRegistro=ELIMINADAS` → SÍ aparece (Acceptance Scenario 3)
- [X] T020 [P] [US3] Extender `PersonaControllerListTest`: con `@WithMockUser(roles =
      "CAPTURISTA")` y `estadoRegistro=ELIMINADAS` en la request, verificar (vía
      `ArgumentCaptor` sobre la llamada mockeada a `PersonaService`) que el filtro
      efectivo enviado al servicio es `ACTIVAS`, no el valor recibido; con
      `@WithMockUser(roles = "ADMIN")` el valor pasa sin alterar

### Implementation for User Story 3

- [X] T021 [US3] Agregar campo `estadoRegistro` (`String`) a
      `PersonaBusquedaFiltroDTO` (`PersonaBusquedaFiltroDTO.java`, creado en T015 sin
      este campo)
- [X] T022 [US3] Agregar a `PersonaSpecifications.java`: `conEstadoRegistro(String
      valorEfectivo)` → `activo = true` / `activo = false` / sin predicado (`TODAS`)
- [X] T023 [US3] Modificar `PersonaController.java`: helper privado `esAdmin()` que
      inspecciona `SecurityContextHolder.getContext().getAuthentication()
      .getAuthorities()` buscando `ROLE_ADMIN` (mismo mecanismo que ya usa
      `JwtAuthenticationFilter`); antes de llamar a `PersonaService`, si no es ADMIN,
      forzar `estadoRegistro = "ACTIVAS"` sin importar lo recibido (FR-008), sin
      lanzar error
- [X] T024 [US3] Modificar `PersonaService.java`: incorporar `conEstadoRegistro(...)`
      a la composición de `Specification` de `listar(PersonaBusquedaFiltroDTO, ...)`
      ya escrita en T017

**Checkpoint**: US1, US2 y US3 funcionan juntas de punta a punta.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Verificación final de que el feature completo cumple spec y constitución

- [ ] T025 Ejecutar manualmente `quickstart.md` de punta a punta contra la aplicación
      levantada localmente (con `002`/`004` también funcionando)
- [ ] T026 Correr la suite completa (`mvn test` y `mvn verify` para los `*IT.java`) y
      confirmar 100% verde, incluida la suite ya adaptada por features anteriores
      (FR-017, SC-006)
- [X] T027 [P] Auditar el repo (`grep -rn "buscarActivas" src/`) y confirmar que no
      queda ninguna referencia al método de repositorio retirado en T003

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: sin dependencias externas — BLOQUEA a las 3 historias
- **US1 (Phase 2)**: depende de Foundational; modifica una sola función dentro de
  `PersonaSpecifications` ya creada en Foundational — el cambio más pequeño y aislado
- **US2 (Phase 3)**: depende de Foundational; introduce `PersonaBusquedaFiltroDTO` y
  reescribe la firma pública de `listar(...)` que consume el controller — **US3
  depende de que esta fase exista** (necesita el mismo DTO y el mismo método de
  servicio para agregar su propio criterio), a diferencia del resto de features
  previas donde las historias eran mutuamente independientes; esta dependencia queda
  documentada explícitamente aquí en vez de forzar una independencia artificial
- **US3 (Phase 4)**: depende de US2 (ver arriba)
- **Polish (Phase 5)**: depende de que US1-US3 estén completas

### Parallel Opportunities

- T002 (Specifications) puede avanzar en paralelo con T005/T006 una vez exista
  T001 (migración) — distintos archivos
- Dentro de cada fase, las tareas marcadas `[P]` (tests sobre archivos distintos, o
  sin dependencia pendiente entre sí) pueden ejecutarse en paralelo

## Implementation Strategy

### MVP First (US1 solamente)

1. Completar Foundational (T001-T006) — refactor sin cambio observable
2. Completar US1 (T007-T009) — mejora de texto insensible a acentos, ya con valor
   independiente de las demás
3. **DETENERSE y VALIDAR**: confirmar `PersonaBusquedaIT` en verde para US1

### Entrega incremental

1. Foundational → refactor completo y regresión verificada
2. US1 → texto insensible a acentos (MVP)
3. US2 → todos los criterios combinables + ordenamiento
4. US3 → restricción de rol sobre `estadoRegistro`
5. Polish → validación de punta a punta y limpieza final

## Phase 6: Convergence

- [X] T028 Agregar a `src/test/java/mx/personas/api/persona/PersonaServiceTest.java` un
      test unitario (mismo patrón `ReflectionTestUtils.invokeMethod` ya usado para
      `fechaNacimientoMinimaDesdeEdadMaxima`/`fechaNacimientoMaximaDesdeEdadMinima`) que
      invoque `aplicarOrden(...)` directamente y confirme: sin `ordenarPor` devuelve el
      mismo `Pageable` recibido, sin `Sort` agregado (FR-011); `"NOMBRE"` ordena por
      `apellidos, nombres`; `"FECHA_NACIMIENTO"` y `"FECHA_REGISTRO"` mapean a la
      propiedad correcta; `direccionOrden` omitido con `ordenarPor` presente usa `ASC`
      por defecto (FR-010, FR-011)

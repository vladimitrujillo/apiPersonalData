---

description: "Task list for Automóviles de Personas y Mantenimientos"
---

# Tasks: Automóviles de Personas y Mantenimientos

**Input**: Design documents from `/specs/008-automoviles-mantenimientos/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUIDOS. El Principio III de la constitución
(`.specify/memory/constitution.md`) es Test-First con Suite Siempre Verde y
NON-NEGOTIABLE. Se incluyen tests para: unicidad de placas (activo-only) y
VIN (global) vía Testcontainers (índices parciales de PostgreSQL, no
reproducibles en H2); consistencia de kilometraje; elegibilidad del
mecánico (persona inexistente/eliminada/sin profesión activa); bloqueo de
mantenimientos sobre automóvil o persona eliminados; ocultamiento y
reaparición de mantenimientos al eliminar/restaurar un automóvil;
inmutabilidad del historial de mantenimientos frente a cambios posteriores
del mecánico; y permisos por rol (ADMIN vs CAPTURISTA).

**Organization**: Tareas agrupadas por historia de usuario (spec.md) para
permitir implementación y prueba independiente de cada una.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias
  pendientes)
- **[Story]**: Historia de usuario a la que pertenece (US1..US7)
- Cada tarea incluye la ruta de archivo exacta

## Path Conventions

Proyecto único Maven/Spring Boot (ver `plan.md` → Project Structure):

- `src/main/java/mx/personas/api/...`
- `src/main/resources/db/migration/...`
- `src/test/java/mx/personas/api/...`

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: Migración, entidades, repositorios y errores compartidos por
las 7 historias de usuario.

**⚠️ CRITICAL**: Ninguna historia de usuario puede implementarse hasta
completar esta fase

- [X] T001 Migración `src/main/resources/db/migration/V8__create_automovil_mantenimiento_pieza.sql`:
      tabla `automovil` (`id UUID PK DEFAULT gen_random_uuid()`, `persona_id
      UUID NOT NULL REFERENCES persona(id)`, `marca VARCHAR(60) NOT NULL`,
      `modelo VARCHAR(60) NOT NULL`, `anio SMALLINT NOT NULL CHECK (anio >=
      1900)`, `color VARCHAR(40)`, `placas VARCHAR(10) NOT NULL`, `vin
      VARCHAR(17)` (cambiado de `CHAR(17)` durante la implementación —
      Hibernate no valida `CHAR` correctamente vía `columnDefinition`, ver
      data-model.md), `activo BOOLEAN NOT NULL DEFAULT true`, columnas de
      `Auditable`); índice único parcial `ux_automovil_placas_activo ON
      automovil (placas) WHERE activo = true`; índice único simple
      `ux_automovil_vin ON automovil (vin)` (global, sin condición de
      estado — data-model.md); índice `ix_automovil_persona_id`; tabla
      `mantenimiento` (`id UUID PK DEFAULT gen_random_uuid()`, `automovil_id
      UUID NOT NULL REFERENCES automovil(id)`, `descripcion TEXT NOT NULL`,
      `fecha DATE NOT NULL CHECK (fecha <= CURRENT_DATE)`, `kilometraje INT
      NOT NULL CHECK (kilometraje >= 0)`, `mecanico_id UUID REFERENCES
      persona(id)`, `costo_total NUMERIC(12,2) NOT NULL CHECK (costo_total
      >= 0)`, `activo BOOLEAN NOT NULL DEFAULT true`, columnas de
      `Auditable`); índices `ix_mantenimiento_automovil_id_fecha ON
      mantenimiento (automovil_id, fecha DESC)`,
      `ix_mantenimiento_mecanico_id`; tabla `pieza_cambiada` (`id UUID PK
      DEFAULT gen_random_uuid()`, `mantenimiento_id UUID NOT NULL
      REFERENCES mantenimiento(id) ON DELETE CASCADE`, `nombre VARCHAR(120)
      NOT NULL`, `numero_parte VARCHAR(40)`, `costo NUMERIC(12,2) CHECK
      (costo IS NULL OR costo >= 0)`; sin columnas de auditoría —
      data-model.md)
- [X] T002 [P] Agregar a `src/main/java/mx/personas/api/common/error/ErrorCode.java`:
      `AUTOMOVIL_PLACAS_DUPLICADAS(HttpStatus.CONFLICT)`,
      `AUTOMOVIL_VIN_DUPLICADO(HttpStatus.CONFLICT)`,
      `AUTOMOVIL_NO_ENCONTRADO(HttpStatus.NOT_FOUND)`,
      `AUTOMOVIL_ELIMINADO(HttpStatus.CONFLICT)`,
      `AUTOMOVIL_YA_ACTIVO(HttpStatus.CONFLICT)`,
      `MANTENIMIENTO_NO_ENCONTRADO(HttpStatus.NOT_FOUND)`,
      `MANTENIMIENTO_YA_ACTIVO(HttpStatus.CONFLICT)`,
      `KILOMETRAJE_INCONSISTENTE(HttpStatus.CONFLICT)`,
      `MECANICO_NO_ENCONTRADO(HttpStatus.BAD_REQUEST)`,
      `MECANICO_ELIMINADO(HttpStatus.CONFLICT)`,
      `MECANICO_SIN_PROFESION_ACTIVA(HttpStatus.CONFLICT)` (contracts/automoviles-mantenimientos-api.md;
      `PERSONA_ELIMINADA` se reutiliza de 007, sin código nuevo)
- [X] T003 [P] Nueva `src/main/java/mx/personas/api/common/error/AutomovilEliminadoException.java`
      (mismo patrón simple que `ProfesionDesactivadaException` — FR-013;
      también cubre registrar/editar un mantenimiento sobre un automóvil
      dado de baja)
- [X] T004 [P] Nueva `src/main/java/mx/personas/api/common/error/AutomovilYaActivoException.java`
      (mismo patrón que `PersonaYaActivaException` — restaurar uno ya activo)
- [X] T005 [P] Nueva `src/main/java/mx/personas/api/common/error/MantenimientoYaActivoException.java`
      (mismo patrón — restaurar uno ya activo)
- [X] T006 [P] Nueva `src/main/java/mx/personas/api/common/error/KilometrajeInconsistenteException.java`
      (FR-017; el mensaje incluye el kilometraje y la fecha del registro
      que lo contradice)
- [X] T007 [P] Nueva `src/main/java/mx/personas/api/common/error/MecanicoNoEncontradoException.java`
      (400 — FR-019; research.md §3)
- [X] T008 [P] Nueva `src/main/java/mx/personas/api/common/error/MecanicoEliminadoException.java`
      (409 — FR-020)
- [X] T009 [P] Nueva `src/main/java/mx/personas/api/common/error/MecanicoSinProfesionActivaException.java`
      (409 — FR-020)
- [X] T010 Nuevo `src/main/java/mx/personas/api/automovil/model/Automovil.java`:
      entidad JPA (tabla `automovil`), extiende `Auditable`; `@Id UUID id`;
      `@ManyToOne(optional = false) Persona persona`; campos
      `marca`/`modelo`/`anio`/`color`/`placas`/`vin`/`activo`; sin setter de
      `vin` tras crearse (inmutable, FR-008); métodos
      `editar(marca, modelo, anio, color, placas)`,
      `desactivar()`/`reactivar()`
- [X] T011 Nuevo `src/main/java/mx/personas/api/automovil/model/PiezaCambiada.java`:
      entidad JPA (tabla `pieza_cambiada`), NO extiende `Auditable`
      (data-model.md); `@Id UUID id`; `@ManyToOne(optional = false)
      Mantenimiento mantenimiento`; campos `nombre`/`numeroParte`/`costo`
- [X] T012 Nuevo `src/main/java/mx/personas/api/automovil/model/Mantenimiento.java`:
      entidad JPA (tabla `mantenimiento`), extiende `Auditable`;
      `@ManyToOne(optional = false) Automovil automovil`;
      `@Column(name = "mecanico_id") UUID mecanicoId` como columna simple
      (sin relación JPA a `Persona`, para no acoplar el agregado — se
      resuelve el nombre completo del mecánico vía `PersonaRepository` en
      el mapper, research.md §9); campos
      `descripcion`/`fecha`/`kilometraje`/`costoTotal`/`activo`;
      `@OneToMany(mappedBy = "mantenimiento", cascade = CascadeType.ALL,
      orphanRemoval = true, fetch = FetchType.EAGER) List<PiezaCambiada>
      piezas` (EAGER agregado durante la implementación: colección pequeña
      por composición, evita `LazyInitializationException` al mapear la
      respuesta fuera de una transacción abierta); método
      `actualizarPiezas(List<PiezaCambiada>)` que hace `clear()` + `addAll()`
      sobre la colección gestionada (research.md §5); métodos
      `editar(...)`, `desactivar()`/`reactivar()`
- [X] T013 Nuevo `src/main/java/mx/personas/api/automovil/repository/AutomovilRepository.java`:
      `boolean existsByPlacasAndActivoTrue(String placas)`;
      `boolean existsByPlacasAndActivoTrueAndIdNot(String placas, UUID id)`
      (para revalidar en `editar`); `boolean existsByVin(String vin)`
      (global, sin condición — solo se usa en `crear`, el VIN es inmutable);
      `List<Automovil> findByPersonaId(UUID personaId)`
- [X] T014 Nuevo `src/main/java/mx/personas/api/automovil/repository/MantenimientoRepository.java`:
      `Page<Mantenimiento> findByAutomovilIdAndActivoTrueOrderByFechaDescCreatedAtDesc(UUID
      automovilId, Pageable pageable)` (historial, FR-022); `Optional<Mantenimiento>
      findFirstByAutomovilIdAndActivoTrueOrderByFechaDescCreatedAtDesc(UUID
      automovilId)` (para validar consistencia de kilometraje al crear,
      research.md §6); `Optional<Mantenimiento>
      findFirstByAutomovilIdAndActivoTrueAndIdNotOrderByFechaDescCreatedAtDesc(UUID
      automovilId, UUID excluirId)` (para validar al editar, excluyéndose a
      sí mismo)
- [X] T015 [P] Nuevo test `src/test/java/mx/personas/api/automovil/repository/AutomovilRepositoryIT.java`
      (Testcontainers, los índices parciales/CHECK son sintaxis PostgreSQL
      que H2 no reproduce): dos automóviles activos con las mismas placas
      violan `ux_automovil_placas_activo`; dos automóviles (uno activo, uno
      eliminado) con las mismas placas NO violan nada; dos automóviles con
      el mismo VIN (sin importar su estado) violan `ux_automovil_vin`
- [X] T016 [P] Nuevo test `src/test/java/mx/personas/api/automovil/repository/MantenimientoRepositoryIT.java`
      (Testcontainers): guardar un `Mantenimiento` con 2 `PiezaCambiada` y
      confirmar que se persisten ambas por cascada;
      `findByAutomovilIdAndActivoTrueOrderByFechaDescCreatedAtDesc` regresa
      los mantenimientos en orden descendente por fecha;
      `findFirstByAutomovilIdAndActivoTrueOrderByFechaDescCreatedAtDesc`
      regresa el de fecha más reciente; reemplazar la colección de piezas
      de un mantenimiento (`clear()` + `addAll()` y `save`) borra las
      piezas huérfanas (orphanRemoval)

**Checkpoint**: migración aplicada, entidades y repositorios listos,
errores declarados. Ninguna historia tiene aún comportamiento observable vía
API.

---

## Phase 2: User Story 1 - Registrar un automóvil a una persona (Priority: P1) 🎯 MVP

**Goal**: Un operador (ADMIN o CAPTURISTA) da de alta un automóvil nuevo
asociado a una persona activa.

**Independent Test**: Dar de alta un automóvil a una persona existente y
confirmar que la operación responde éxito con los datos capturados, sin
depender de mantenimientos.

### Implementation for User Story 1

- [X] T017 [P] [US1] Nuevo `src/main/java/mx/personas/api/automovil/dto/AutomovilRequestDTO.java`:
      `marca`/`modelo`/`anio`/`color`/`placas`/`vin` (`marca`, `modelo`,
      `anio`, `placas` con `@NotBlank`/`@NotNull`; `color` y `vin`
      opcionales)
- [X] T018 [P] [US1] Nuevo `src/main/java/mx/personas/api/automovil/dto/AutomovilResponseDTO.java`:
      `id`, `personaId`, `marca`, `modelo`, `anio`, `color`, `placas`,
      `vin`, `activo`
- [X] T019 [US1] Nuevo `src/main/java/mx/personas/api/automovil/mapper/AutomovilMapper.java`
      (MapStruct, interfaz simple `toResponseDTO(Automovil)`)
- [X] T020 [US1] Extender `src/main/java/mx/personas/api/persona/service/HistorialDiffService.java`
      con `serializarAltaAutomovil(Automovil)` (mismo patrón que
      `serializarCreacion`, campos `automovil.marca/modelo/anio/color/placas/vin`)
- [X] T021 [US1] Nuevo `src/main/java/mx/personas/api/automovil/service/AutomovilService.java`:
      método `crear(personaId, AutomovilRequestDTO)` — valida persona
      existente y activa (404 `PERSONA_NO_ENCONTRADA` / 409
      `PERSONA_ELIMINADA`), año entre 1900 y año actual + 1 (service, no
      CHECK — research.md §7), `AutomovilRepository.existsByPlacasAndActivoTrue`
      (409 `AUTOMOVIL_PLACAS_DUPLICADAS`), `existsByVin` si se proporciona
      (409 `AUTOMOVIL_VIN_DUPLICADO`); guarda y registra historial en la
      persona dueña (`TipoOperacion.CREACION`, mismo método
      `registrarHistorial` que ya usa `PersonaProfesionService`, o uno
      análogo inyectando `PersonaHistorialRepository`/`SecurityAuditorAware`)
- [X] T022 [US1] Extender `src/main/java/mx/personas/api/persona/controller/PersonaController.java`
      con `POST /api/personas/{id}/automoviles` (ADMIN y CAPTURISTA, 201)
- [X] T023 [US1] Nuevo test `src/test/java/mx/personas/api/integration/AutomovilRegistrarIT.java`
      (Testcontainers): alta exitosa con VIN; alta exitosa sin VIN; placas
      duplicadas de un automóvil activo → 409; placas de un automóvil
      eliminado reutilizables → 201; VIN duplicado (contra uno activo o uno
      eliminado) → 409; persona eliminada lógicamente → 409; año fuera de
      rango (1899 y año actual + 2) → 400

**Checkpoint**: US1 completa y probada de forma independiente.

---

## Phase 3: User Story 2 - Consultar los automóviles de una persona (Priority: P1)

**Goal**: Un operador consulta el listado de automóviles de una persona y el
detalle de uno en particular.

**Independent Test**: Registrar automóviles a una persona y confirmar que el
listado y el detalle regresan exactamente los datos capturados.

### Implementation for User Story 2

- [X] T024 [US2] Extender `AutomovilService.java` con
      `listarPorPersona(personaId)` (valida que la persona exista, 404 si
      no; regresa únicamente los `Automovil` con `activo = true` de esa
      persona — un automóvil dado de baja desaparece de este listado,
      FR-010/US7, hallazgo F4 de `/speckit-analyze`) y
      `obtenerPorId(automovilId)` (404 `AUTOMOVIL_NO_ENCONTRADO` si no
      existe o si `activo = false`)
- [X] T025 [US2] Extender `PersonaController.java` con
      `GET /api/personas/{id}/automoviles` (ADMIN y CAPTURISTA, sin
      paginar — Assumptions de spec.md)
- [X] T026 [US2] Nuevo `src/main/java/mx/personas/api/automovil/controller/AutomovilController.java`
      con `GET /api/automoviles/{id}` (ADMIN y CAPTURISTA)
- [X] T027 [US2] Nuevo test `src/test/java/mx/personas/api/integration/AutomovilConsultarIT.java`:
      persona con dos automóviles activos → listado regresa ambos; detalle
      de un automóvil regresa marca/modelo/año/color/placas/vin/activo;
      persona sin automóviles → lista vacía; automóvil inexistente → 404;
      persona con un automóvil activo y uno dado de baja → el listado
      regresa solo el activo (hallazgo F4 de `/speckit-analyze`)

**Checkpoint**: US1 y US2 completas y probadas de forma independiente.

---

## Phase 4: User Story 3 - Registrar un mantenimiento con sus piezas (Priority: P1)

**Goal**: Un operador registra, en una sola operación, un mantenimiento con
sus piezas cambiadas y, opcionalmente, el mecánico que lo realizó.

**Independent Test**: Registrar un mantenimiento (con y sin piezas/mecánico)
sobre un automóvil existente y confirmar que la operación responde éxito
con todos los datos capturados, y rechaza cada regla de negocio (fecha,
costos, kilometraje, mecánico, automóvil/persona eliminados).

### Implementation for User Story 3

- [X] T028 [P] [US3] Nuevo `src/main/java/mx/personas/api/automovil/dto/PiezaCambiadaDTO.java`:
      `id` (nullable en request, presente en response), `nombre`
      (`@NotBlank`), `numeroParte` (opcional), `costo` (opcional)
- [X] T029 [P] [US3] Nuevo `src/main/java/mx/personas/api/automovil/dto/MecanicoResumenDTO.java`:
      `id`, `nombreCompleto` (proyección mínima — research.md §9)
- [X] T030 [P] [US3] Nuevo `src/main/java/mx/personas/api/automovil/dto/MantenimientoRequestDTO.java`:
      `descripcion` (`@NotBlank`), `fecha` (`@NotNull`), `kilometraje`
      (`@NotNull @PositiveOrZero`), `costoTotal` (`@NotNull
      @PositiveOrZero`), `mecanicoId` (opcional), `piezas`
      (`List<PiezaCambiadaDTO>`, opcional, default `[]`)
- [X] T031 [P] [US3] Nuevo `src/main/java/mx/personas/api/automovil/dto/MantenimientoResponseDTO.java`:
      `id`, `automovilId`, `descripcion`, `fecha`, `kilometraje`,
      `costoTotal`, `mecanico` (`MecanicoResumenDTO`, nullable), `piezas`
      (`List<PiezaCambiadaDTO>`), `activo`
- [X] T032 [US3] Nuevo `src/main/java/mx/personas/api/automovil/mapper/MantenimientoMapper.java`
      (MapStruct `@Mapper(componentModel = "spring")` abstracto, con
      `@Autowired PersonaRepository` para resolver `mecanico.nombreCompleto`
      a partir de `mantenimiento.mecanicoId` — mismo patrón que
      `DireccionMapper.resolverLogin`)
- [X] T033 [US3] Extender `HistorialDiffService.java` con
      `serializarRegistroMantenimiento(Mantenimiento)` (campos
      `mantenimiento.descripcion/fecha/kilometraje/costoTotal`, y
      `mantenimiento.mecanicoId` si aplica — nunca datos personales del
      mecánico más allá de su id)
- [X] T034 [US3] Nuevo `src/main/java/mx/personas/api/automovil/service/MantenimientoService.java`:
      método `registrar(automovilId, MantenimientoRequestDTO)` — valida
      automóvil existente y activo (404 `AUTOMOVIL_NO_ENCONTRADO` / 409
      `AutomovilEliminadoException`), persona dueña activa (409
      `PersonaEliminadaException`, reutilizada de 007), fecha no futura
      (400), costo total y costo de cada pieza `>= 0` (400), kilometraje
      `>= 0` (400); consistencia de kilometraje contra
      `findFirstByAutomovilIdAndActivoTrueOrderByFechaDescCreatedAtDesc`
      (409 `KilometrajeInconsistenteException`, research.md §6); si
      `mecanicoId` viene informado, compone
      `PersonaRepository.findById` (400 `MecanicoNoEncontradoException` si
      vacío; 409 `MecanicoEliminadoException` si `!activo`),
      `ProfesionRepository.findByNombreNormalizado("Mecánico")` +
      `PersonaProfesionRepository.existsByPersonaIdAndProfesionIdAndActivoTrue`
      (409 `MecanicoSinProfesionActivaException` si `false` —
      research.md §2-3); persiste el `Mantenimiento` con sus
      `PiezaCambiada` (cascada) en una sola transacción `@Transactional`
      (research.md §10) y registra historial en la persona dueña del
      automóvil
- [X] T035 [US3] Nuevo `AutomovilController.java` con
      `POST /api/automoviles/{id}/mantenimientos` (ADMIN y CAPTURISTA, 201)
- [X] T036 [US3] Nuevo test `src/test/java/mx/personas/api/integration/MantenimientoRegistrarIT.java`
      (Testcontainers; usa la profesión "Mecánico" sembrada por 007 y
      `PersonaProfesionRepository`/endpoint de asignar para preparar un
      mecánico elegible): alta con piezas y mecánico elegible; alta sin
      piezas ni mecánico; fecha futura → 400; costo total negativo → 400;
      costo de una pieza negativo → 400; kilometraje negativo → 400;
      kilometraje menor al mantenimiento más reciente del automóvil → 409
      con el kilometraje/fecha en conflicto; `mecanicoId` inexistente →
      400; `mecanicoId` de persona eliminada lógicamente → 409; `mecanicoId`
      de persona activa sin la profesión "Mecánico" activa → 409;
      automóvil eliminado lógicamente → 409; persona dueña del automóvil
      eliminada lógicamente → 409

**Checkpoint**: US1, US2 y US3 completas y probadas de forma independiente.

---

## Phase 5: User Story 4 - Consultar el historial y el detalle de mantenimientos (Priority: P1)

**Goal**: Un operador consulta, paginado y ordenado por fecha descendente,
el historial de mantenimientos de un automóvil, y el detalle completo de
uno con sus piezas y los datos básicos del mecánico.

**Independent Test**: Registrar varios mantenimientos a un automóvil y
confirmar que el historial y el detalle regresan exactamente lo capturado,
en el orden correcto, y que cambios posteriores al mecánico no alteran lo
ya registrado.

### Implementation for User Story 4

- [X] T037 [US4] Nuevo `src/main/java/mx/personas/api/automovil/dto/MantenimientoPageResponseDTO.java`:
      `contenido` (`List<MantenimientoResponseDTO>`), `pagina`,
      `tamanoPagina`, `totalElementos`, `totalPaginas` (mismo shape que el
      resto de listados paginados del proyecto)
- [X] T038 [US4] Extender `MantenimientoService.java` con
      `listarHistorial(automovilId, pageable)` (valida que el automóvil
      exista y esté activo, 404 `AUTOMOVIL_NO_ENCONTRADO` si no — ajustado
      durante la implementación de US7 para no reutilizar la validación de
      escritura de `registrar`, que lanzaba 409 en vez de 404 para esta
      consulta; usa
      `findByAutomovilIdAndActivoTrueOrderByFechaDescCreatedAtDesc`) y
      `obtenerPorId(mantenimientoId)` (404 `MANTENIMIENTO_NO_ENCONTRADO` si
      no existe, si `mantenimiento.activo = false`, o si
      `mantenimiento.automovil.activo = false` — FR-010 exige ocultar el
      historial de un automóvil eliminado de "cualquier consulta", no solo
      del listado paginado; hallazgo F1 CRITICAL de `/speckit-analyze`)
- [X] T039 [US4] Extender `AutomovilController.java` con
      `GET /api/automoviles/{id}/mantenimientos` (ADMIN y CAPTURISTA,
      paginado — `page`/`size`, máx. 100, por defecto 20)
- [X] T040 [US4] Nuevo `src/main/java/mx/personas/api/automovil/controller/MantenimientoController.java`
      con `GET /api/mantenimientos/{id}` (ADMIN y CAPTURISTA)
- [X] T041 [US4] Nuevo test `src/test/java/mx/personas/api/integration/MantenimientoConsultarIT.java`:
      automóvil con tres mantenimientos en fechas distintas → historial
      ordenado de la fecha más reciente a la más antigua; detalle de un
      mantenimiento con piezas y mecánico → incluye piezas y
      `{id, nombreCompleto}` del mecánico, sin el resto de sus datos
      personales; retirarle la profesión "Mecánico" a la persona referenciada
      (o eliminarla lógicamente) después de registrado el mantenimiento →
      el detalle del mantenimiento no cambia (FR-021/SC-005); automóvil sin
      mantenimientos → página vacía; `GET /api/mantenimientos/{id}` sobre un
      mantenimiento activo cuyo automóvil fue eliminado lógicamente → 404
      (hallazgo F1 CRITICAL de `/speckit-analyze`; el caso de un
      mantenimiento eliminado directamente ya lo cubre T051)

**Checkpoint**: US1-US4 (todas las P1) completas y probadas de forma
independiente.

---

## Phase 6: User Story 5 - Actualizar los datos de un automóvil (Priority: P2)

**Goal**: Un operador corrige marca, modelo, año, color o placas de un
automóvil ya registrado.

**Independent Test**: Editar un automóvil existente y confirmar que los
nuevos valores se reflejan en su detalle, que el VIN no se puede cambiar, y
que las placas se revalidan contra duplicados activos.

### Implementation for User Story 5

- [X] T042 [P] [US5] Nuevo `src/main/java/mx/personas/api/automovil/dto/AutomovilUpdateDTO.java`:
      `marca`/`modelo`/`anio`/`color`/`placas`, todos opcionales (sin
      `vin` — inmutable, FR-008)
- [X] T043 [US5] Extender `HistorialDiffService.java` con
      `serializarEdicionAutomovil(AutomovilSnapshot antes, Automovil
      actual)` (diff campo por campo de
      marca/modelo/anio/color/placas, mismo patrón que
      `serializarModificacion` de Persona)
- [X] T044 [US5] Extender `AutomovilService.java` con
      `editar(automovilId, AutomovilUpdateDTO)` — solo sobre automóvil
      activo (404 `AUTOMOVIL_NO_ENCONTRADO` si no existe; 409
      `AutomovilEliminadoException` si `activo = false` — FR-008 exige
      editar "un automóvil activo"; cita corregida, hallazgo F2 de
      `/speckit-analyze`: la fuente correcta es FR-008, no
      research.md/Assumptions); revalida año y unicidad de placas
      (`existsByPlacasAndActivoTrueAndIdNot`) si cambian; registra
      historial si hubo cambios
- [X] T045 [US5] Extender `AutomovilController.java` con
      `PATCH /api/automoviles/{id}` (ADMIN y CAPTURISTA)
- [X] T046 [US5] Nuevo test `src/test/java/mx/personas/api/integration/AutomovilActualizarIT.java`:
      edición exitosa de marca/modelo/año/color/placas; intento de cambiar
      el VIN vía el endpoint de edición no lo modifica (el campo no existe
      en `AutomovilUpdateDTO`, se confirma que el VIN original persiste);
      placas editadas a unas ya activas en otro automóvil → 409; año fuera
      de rango → 400; editar un automóvil dado de baja → 409
      `AUTOMOVIL_ELIMINADO` (FR-008, hallazgo F2 de `/speckit-analyze`)

**Checkpoint**: US1-US5 completas y probadas de forma independiente.

---

## Phase 7: User Story 6 - Actualizar, eliminar y restaurar un mantenimiento (Priority: P2)

**Goal**: Un operador corrige un mantenimiento existente; un ADMIN lo
elimina lógicamente y puede restaurarlo más adelante.

**Independent Test**: Editar un mantenimiento y confirmar el cambio;
eliminarlo lógicamente y confirmar que desaparece del historial; restaurarlo
y confirmar que reaparece intacto; confirmar que CAPTURISTA no puede
eliminar ni restaurar.

### Implementation for User Story 6

- [X] T047 [P] [US6] Nuevo `src/main/java/mx/personas/api/automovil/dto/MantenimientoUpdateDTO.java`:
      mismos campos opcionales que `MantenimientoRequestDTO` (sin
      `automovilId` — no reparentable, Assumptions de spec.md); `piezas`,
      si se envía, siempre reemplaza el conjunto completo
- [X] T048 [US6] Extender `HistorialDiffService.java` con
      `serializarEdicionMantenimiento(antes, actual)`,
      `serializarBajaMantenimiento(Mantenimiento)`,
      `serializarRestauracionMantenimiento(Mantenimiento)`
- [X] T049 [US6] Extender `MantenimientoService.java` con
      `editar(mantenimientoId, MantenimientoUpdateDTO)` (mismas
      validaciones que `registrar`: fecha, costos, kilometraje —
      consistencia excluyéndose a sí mismo vía
      `findFirstByAutomovilIdAndActivoTrueAndIdNotOrderByFechaDescCreatedAtDesc`,
      research.md §6 —, mecánico si cambia, Y TAMBIÉN 409
      `AutomovilEliminadoException`/`PersonaEliminadaException` si el
      automóvil o su persona dueña están eliminados lógicamente — FR-024
      (redactado explícitamente tras hallazgo F3 de `/speckit-analyze`);
      reemplaza piezas con `actualizarPiezas` si se envían),
      `eliminar(mantenimientoId)` (solo ADMIN, 404 si no existe) y
      `restaurar(mantenimientoId)` (solo ADMIN, 404 si no existe, 409
      `MantenimientoYaActivoException` si ya está activo)
- [X] T050 [US6] Extender `MantenimientoController.java` con
      `PATCH /api/mantenimientos/{id}` (ADMIN y CAPTURISTA),
      `DELETE /api/mantenimientos/{id}` (solo ADMIN, 204),
      `POST /api/mantenimientos/{id}/restaurar` (solo ADMIN, 200)
- [X] T051 [US6] Nuevo test `src/test/java/mx/personas/api/integration/MantenimientoActualizarEliminarIT.java`:
      edición exitosa de descripción/costo/piezas (con las mismas
      validaciones que el alta); CAPTURISTA intenta eliminar → 403; ADMIN
      elimina → deja de aparecer en el historial del automóvil; CAPTURISTA
      intenta restaurar → 403; ADMIN restaura → reaparece en el historial
      con sus piezas intactas; ADMIN intenta restaurar uno ya activo → 409
      `MANTENIMIENTO_YA_ACTIVO`; editar un mantenimiento cuyo automóvil fue
      eliminado lógicamente → 409 `AUTOMOVIL_ELIMINADO`; editar un
      mantenimiento cuya persona dueña del automóvil fue eliminada
      lógicamente → 409 `PERSONA_ELIMINADA` (FR-024, hallazgo F3 de
      `/speckit-analyze`)

**Checkpoint**: US1-US6 completas y probadas de forma independiente.

---

## Phase 8: User Story 7 - Eliminar y restaurar un automóvil (Priority: P2)

**Goal**: Un ADMIN da de baja lógicamente un automóvil (ocultando también su
historial de mantenimientos) y puede restaurarlo más adelante.

**Independent Test**: Eliminar lógicamente un automóvil con mantenimientos
previos, confirmar que tanto él como su historial dejan de ser consultables,
y restaurarlo confirmando que ambos reaparecen intactos; confirmar que
CAPTURISTA no puede eliminar ni restaurar.

### Implementation for User Story 7

- [X] T052 [US7] Extender `HistorialDiffService.java` con
      `serializarBajaAutomovil(Automovil)`,
      `serializarRestauracionAutomovil(Automovil)`
- [X] T053 [US7] Extender `AutomovilService.java` con
      `eliminar(automovilId)` (solo ADMIN; 404 si no existe; desactiva el
      automóvil — FR-010, `listarPorPersona`/`obtenerPorId` y el historial
      de mantenimientos ya excluyen por `activo=false` a nivel de query,
      sin necesitar tocar los mantenimientos) y `restaurar(automovilId)`
      (solo ADMIN; 404 si no existe; 409 `AutomovilYaActivoException` si ya
      está activo)
- [X] T054 [US7] Extender `AutomovilController.java` con
      `DELETE /api/automoviles/{id}` (solo ADMIN, 204),
      `POST /api/automoviles/{id}/restaurar` (solo ADMIN, 200)
- [X] T055 [US7] Nuevo test `src/test/java/mx/personas/api/integration/AutomovilEliminarRestaurarIT.java`:
      automóvil activo con mantenimientos previos; CAPTURISTA intenta
      eliminarlo → 403; ADMIN lo elimina → el automóvil (`GET
      /api/automoviles/{id}` → 404) y su historial (`GET
      .../mantenimientos` → 404) dejan de ser consultables; CAPTURISTA
      intenta restaurarlo → 403; ADMIN lo restaura → automóvil y su
      historial de mantenimientos vuelven a ser consultables, intactos;
      ADMIN intenta restaurar uno ya activo → 409 `AUTOMOVIL_YA_ACTIVO`

**Checkpoint**: Las 7 historias de usuario completas y probadas de forma
independiente.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Validación de punta a punta y cierre de la feature.

- [X] T056 Ejecutar manualmente los 8 escenarios de `quickstart.md` contra
      la API corriendo (Docker o local), confirmando cada resultado
      esperado (SC-001 a SC-009)
- [X] T057 Correr la suite completa (`mvn -o test
      -Dmaven.compiler.useIncrementalCompilation=false` y `mvn -o verify
      -Dmaven.compiler.useIncrementalCompilation=false`) y confirmar 100%
      verde, incluyendo toda la suite de `007-profesiones-personas` y
      anteriores (FR-029/SC-009). Nota de implementación: se encontró y
      corrigió una colisión de aislamiento entre clases de test —
      `AutomovilRepositoryIT`, `AutomovilRegistrarIT` y
      `AutomovilActualizarIT` usaban el mismo VIN literal de ejemplo
      (`"1HGCM82633A004352"`); como el VIN tiene unicidad GLOBAL y las
      clases IT comparten el mismo contenedor Testcontainers dentro de una
      misma corrida de `mvn verify`, la clase que corriera segunda fallaba
      con `AUTOMOVIL_VIN_DUPLICADO`. Cada test ahora genera su propio VIN
      único con `TestUniqueId.homoclave()`.
- [X] T058 Confirmar contra `/v3/api-docs` que ningún endpoint existente
      (`001`, `002`, `003`, `004`, `005`, `006`, `007`) cambió de forma ni
      de código de estado — solo se agregaron rutas nuevas

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: Sin dependencias — bloquea todas las historias
- **User Stories (Phase 2-8)**: Todas dependen de Foundational.
  US1 y US2 son la base mínima (MVP: alta + consulta de automóviles). US3 y
  US4 (mantenimientos) dependen conceptualmente de que exista un automóvil
  (US1), pero su código es independiente de US2/US5/US6/US7. US5 (editar
  automóvil), US6 (editar/eliminar/restaurar mantenimiento) y US7
  (eliminar/restaurar automóvil) son P2 y pueden implementarse en cualquier
  orden entre sí una vez completas US1/US3.
- **Polish (Phase 9)**: Depende de que todas las historias deseadas estén
  completas.

### Within Each User Story

- DTOs (marcados [P]) antes que mapper/service
- Extensión de `HistorialDiffService` antes que el método de service que la
  usa
- Service antes que controller
- Controller antes que el test de integración (aunque, por Principio III,
  el test debe escribirse y fallar antes de completar la implementación)

### Parallel Opportunities

- Todas las tareas de Foundational marcadas [P] (T002-T009, T015-T016)
- Los DTOs de una misma historia marcados [P] (p. ej. T017-T018, T028-T031,
  T042, T047)
- Distintas historias de usuario P2 (US5, US6, US7) pueden trabajarse en
  paralelo por distintos desarrolladores una vez completas US1/US3

---

## Parallel Example: User Story 3

```bash
# DTOs de la historia 3 en paralelo (archivos distintos, sin dependencias entre sí):
Task: "Nuevo PiezaCambiadaDTO.java"
Task: "Nuevo MecanicoResumenDTO.java"
Task: "Nuevo MantenimientoRequestDTO.java"
Task: "Nuevo MantenimientoResponseDTO.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 + 2)

1. Completar Phase 1: Foundational (CRÍTICO — bloquea todo lo demás)
2. Completar Phase 2: US1 (registrar automóvil)
3. Completar Phase 3: US2 (consultar automóviles)
4. **DETENERSE Y VALIDAR**: alta + consulta de automóviles funcionando de
   punta a punta, de forma independiente de mantenimientos
5. Continuar con US3/US4 (mantenimientos, también P1) para completar el
   valor central del feature

### Incremental Delivery

1. Foundational → base lista
2. US1 + US2 → MVP de automóviles (sin mantenimientos)
3. US3 + US4 → mantenimientos con piezas y mecánico, historial consultable
4. US5 → edición de automóvil
5. US6 → edición/baja/restauración de mantenimiento
6. US7 → baja/restauración de automóvil
7. Polish → validación de punta a punta y reporte final

---

## Notes

- [P] tasks = archivos distintos, sin dependencias entre sí
- [Story] mapea cada tarea a su historia de usuario para trazabilidad
- Cada historia debe ser completable y probable de forma independiente
- Verificar que los tests fallan antes de implementar (Principio III)
- Evitar: tareas vagas, conflictos de mismo archivo dentro de una misma
  historia, dependencias cruzadas entre historias que rompan su
  independencia

---

## Phase 10: Convergence

- [X] T059 Agregar al menos una aserción contra `GET
      /api/personas/{id}/historial` por cada una de las 8 operaciones de
      mutación (alta/edición/baja/restauración de un automóvil; registro/
      edición/baja/restauración de un mantenimiento) confirmando que el
      historial de auditoría de la persona dueña crece en una entrada —
      la implementación ya está correctamente conectada (verificado en
      vivo), pero ningún test de la suite lo protege contra regresión
      per FR-028 (partial)
- [X] T060 Agregar un escenario a
      `MantenimientoRegistrarIT.fechaFuturaCostoNegativoOKilometrajeNegativoResponden400`
      (o un test nuevo) que envíe una pieza con `costo` negativo y
      confirme `400` — la validación ya funciona correctamente en
      producción (`@PositiveOrZero` cascada vía `@Valid`), pero
      `tasks.md` T036 prometía este escenario explícitamente y no quedó
      cubierto per FR-015 (partial)

---

## Phase 11: Convergence

- [X] T061 Fortalecer las 6 aserciones de historial de auditoría agregadas
      en T059 para edición/baja/restauración de automóvil
      (`AutomovilActualizarIT.editarMarcaModeloAnioColorPlacas`,
      `AutomovilEliminarRestaurarIT` ambos tests) y de mantenimiento
      (`MantenimientoActualizarEliminarIT`, los tres checkpoints):
      capturar `historialAuditoriaDe(personaId).size()` antes de la
      acción y, después, usar `hasSizeGreaterThan(tamañoAntes)` en vez de
      `anySatisfy(operacion == MODIFICACION)` — esta última es
      trivialmente satisfecha por la entrada que ya dejó el alta previa
      en el mismo test, por lo que no detectaría una regresión si la
      propia llamada a `registrarHistorial` de la edición/baja/
      restauración se eliminara por error per FR-028 (partial)

---

## Phase 12: Convergence

- [X] T062 Fortalecer la aserción de historial de auditoría en
      `MantenimientoRegistrarIT.registrarConPiezasYMecanicoElegibleYSinNinguno`
      (agregada en T059, no cubierta por el alcance de T061): capturar
      `historialAuditoriaDe(personaId).size()` justo antes de registrar
      el mantenimiento (después de `crearAutomovil`/`crearMecanicoElegible`)
      y usar `hasSizeGreaterThan(tamañoAntes)` en vez de
      `anySatisfy(operacion == MODIFICACION)` — esta última ya la
      satisface la entrada que deja `crearAutomovil` (una alta de
      automóvil) antes de llegar al registro del mantenimiento bajo
      prueba per FR-028 (partial)

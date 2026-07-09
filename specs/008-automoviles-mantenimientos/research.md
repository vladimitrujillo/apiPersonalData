# Research: Automóviles de Personas y Mantenimientos

## 1. VIN vs placas: unicidad (Principio VI, decisiones D2/D3 del proyecto)

**Decision**: VIN, cuando se proporciona, tiene unicidad global absoluta (índice
único simple sobre la columna, sin condición de estado) e inmutable tras el
alta. Placas tienen unicidad solo entre automóviles activos (índice único
parcial `WHERE activo = true`) y son editables, revalidando la unicidad en
cada edición.

**Rationale**: El propio feature lo pide explícitamente por analogía directa
con decisiones ya tomadas en el proyecto: la CURP de una persona (identidad
vitalicia, D2) tiene unicidad global sin excepción por estado; el correo
(dato de contacto, D3) tiene unicidad solo entre activos y se libera al
desactivarse el registro. El VIN identifica de forma permanente al vehículo
físico (como la CURP a la persona); las placas son una asignación
administrativa que puede cambiar o reasignarse (como el correo).

**Alternatives considered**: Unicidad global también para placas — rechazada,
contradice explícitamente la petición del usuario y el precedente de correo.
Permitir editar el VIN — rechazada, rompería su rol de identificador
permanente del vehículo.

## 2. Elegibilidad del mecánico: reutilizar el módulo de profesiones (007)

**Decision**: La validación de "persona activa con la profesión Mecánico
asignada de forma activa" se resuelve componiendo repositorios ya existentes
de `007-profesiones-personas`, sin duplicar queries ni crear una tabla
paralela:

1. `PersonaRepository.findById(mecanicoId)` → si está vacío, `MECANICO_NO_ENCONTRADO` (400).
2. Si existe pero `persona.activo == false` → `MECANICO_ELIMINADO` (409).
3. `ProfesionRepository.findByNombreNormalizado("Mecánico")` → obtiene el id de la profesión semilla (siempre existe, V7).
4. `PersonaProfesionRepository.existsByPersonaIdAndProfesionIdAndActivoTrue(mecanicoId, profesionMecanicoId)` → si es `false`, `MECANICO_SIN_PROFESION_ACTIVA` (409).

Esta composición vive en un método nuevo de `MantenimientoService` (no se
agrega a `PersonaProfesionService`, para no acoplar el dominio `profesion` al
dominio `automovil` — es este último quien depende de aquel, nunca al revés).

**Rationale**: `existsByPersonaIdAndProfesionIdAndActivoTrue` ya existe
exactamente con esta forma (T013 de 007, usado originalmente para el 409 de
"ya asignada"); reutilizarlo evita una query nueva y mantiene una única
fuente de verdad sobre "quién es mecánico activo ahora mismo".

**Alternatives considered**: Cachear o desnormalizar "es mecánico" en la
tabla `persona` — rechazado, violaría la Fuente Única de Verdad que ya vive
en `persona_profesion` y requeriría sincronización adicional. Crear un nuevo
repositorio de solo-lectura específico para "mecánicos elegibles" — rechazado
por ser una duplicación innecesaria de una query de una sola línea.

## 3. 400 vs 409 vs 404 para las tres condiciones de rechazo del mecánico

**Decision**: `MECANICO_NO_ENCONTRADO` es `400 BAD_REQUEST` (no 404), mientras
que `MECANICO_ELIMINADO` y `MECANICO_SIN_PROFESION_ACTIVA` son `409 CONFLICT`.
Esto es una decisión deliberada del usuario en la especificación, distinta al
precedente de `CP_NO_ENCONTRADO` (404) para códigos postales.

**Rationale**: La distinción semántica es: un identificador que no
corresponde a *ninguna* persona es un valor de request inválido en el campo
`mecanicoId` (como una validación de formato, de ahí 400), mientras que un
identificador que sí corresponde a una persona real pero no es elegible en
este momento (eliminada, o sin la profesión activa) es un conflicto de
negocio con el estado actual del sistema (409) — igual que
`PERSONA_PROFESION_YA_ASIGNADA` o `PROFESION_DESACTIVADA` en 007. Esta
distinción 400/409 fue solicitada explícitamente por el usuario y se respeta
tal cual, documentándose aquí como una excepción consciente al patrón
CP/colonia (donde "no existe" es 404) para que futuras features no lo copien
por error sin releer esta nota.

**Alternatives considered**: 404 para mecánico inexistente (consistente con
`CP_NO_ENCONTRADO`/`PROFESION_NO_ENCONTRADA`) — rechazada por instrucción
explícita del usuario en la especificación original.

## 4. Auditoría: extender `persona_historial` vía la persona dueña (no una tabla nueva)

**Decision**: Las altas/ediciones/bajas/restauraciones de `Automovil`, y los
registros/ediciones/bajas/restauraciones de `Mantenimiento`, se auditan
extendiendo `HistorialDiffService` con nuevos métodos `serializar*` y
escribiendo la entrada resultante en `persona_historial`, atribuida a la
`Persona` dueña del automóvil (no se crea una tabla `automovil_historial`
nueva).

**Rationale**: Es el mismo patrón ya usado por `persona_profesion` en 007
(`serializarAsignacionProfesion`/`serializarRetiroProfesion`, con
`TipoOperacion.MODIFICACION` sobre la persona dueña) — un sub-recurso de una
persona que no tiene su propia tabla de auditoría, sino que reutiliza la de
su dueño. `Profesion` (el catálogo, sin dueño-persona) en cambio solo se
audita vía las columnas `Auditable` (creadoPor/actualizadoPor); `Automovil` y
`Mantenimiento` sí tienen una persona dueña identificable (directa o vía el
automóvil), así que corresponde el patrón de `persona_profesion`, no el de
`Profesion`.

**Alternatives considered**: Tabla `automovil_historial` dedicada — rechazada
por Principio I (seguir las convenciones existentes) y porque fragmentaría
la vista de auditoría de una persona en dos lugares distintos sin necesidad.

## 5. Modelo de `PiezaCambiada`: entidad propia con cascada, no `@ElementCollection`

**Decision**: `PiezaCambiada` es una `@Entity` JPA con su propia PK (`UUID`),
relacionada `@ManyToOne` a `Mantenimiento`, gestionada como composición desde
`Mantenimiento` (`@OneToMany(cascade = ALL, orphanRemoval = true)`). No
extiende `Auditable` (no tiene columnas de auditoría en la migración) ni
tiene repositorio ni controller propios.

**Rationale**: La migración le da un PK propio (`UUID`), lo cual descarta
`@ElementCollection` (que requeriría una clave compuesta o embebida sin id
propio). `orphanRemoval = true` + reemplazar la colección completa en
`Mantenimiento.actualizarPiezas(List<PiezaCambiada>)` implementa exactamente
"en updates se reemplaza la colección completa" sin necesitar lógica de diff
manual: JPA borra las filas huérfanas y inserta las nuevas al hacer
`clear()` + `addAll()` sobre la colección gestionada.

**Alternatives considered**: `@ElementCollection` con clase embebida —
rechazada porque el esquema exige un PK propio por fila. Repositorio y
endpoints propios para piezas — rechazado explícitamente por el usuario
("sin endpoints propios").

## 6. Consistencia de kilometraje: comparación global, no contra el vecino cronológico

**Decision**: `MantenimientoRepository` expone
`findFirstByAutomovilIdAndActivoTrueOrderByFechaDescCreatedAtDesc(automovilId)`
(o equivalente) para obtener el mantenimiento activo con la fecha más
reciente de un automóvil. El servicio compara el kilometraje del
registro nuevo/editado únicamente contra ese resultado, sin buscar vecinos
por fecha. En `update`, la consulta se ejecuta excluyendo el propio
`mantenimientoId` que se está editando (`...AndIdNot(id)`).

**Rationale**: Resuelto explícitamente en `/speckit-clarify` (sesión
2026-07-08): es la lectura literal de la especificación y la más simple de
implementar/probar; el caso de backfill con fecha pasada queda fuera de
alcance en esta versión (documentado en spec.md como Edge Case).

**Alternatives considered**: Consistencia cronológica completa (contra
vecino anterior y posterior por fecha) — descartada en clarify por mayor
complejidad sin que el usuario la haya pedido.

## 7. Año máximo del automóvil: validación en el service, no CHECK de BD

**Decision**: La migración solo declara `CHECK (anio >= 1900)`. El límite
superior (año actual + 1) se valida en `AutomovilService` al crear/editar,
no con un segundo `CHECK` de base de datos.

**Rationale**: Un `CHECK` que dependa de `CURRENT_DATE`/`now()` fijaría el
límite superior al momento en que Postgres *evalúa* el constraint (que es en
cada INSERT/UPDATE, no en el momento de aplicar la migración, así que
técnicamente sería correcto) — pero el proyecto prefiere mantener esta
regla, que cambia con el tiempo (el año actual avanza cada 31 de diciembre),
en el service, donde es explícita, testeable con `Clock`/`LocalDate.now()`
mockeable, y consistente con cómo se validan otras reglas temporales del
proyecto (p. ej. `FECHA_NACIMIENTO_FUTURA` en `PersonaService`, no en un
CHECK de BD).

**Alternatives considered**: `CHECK (anio <= EXTRACT(YEAR FROM CURRENT_DATE) + 1)`
— técnicamente viable en PostgreSQL, pero rechazada por el usuario
explícitamente y por consistencia con el patrón ya usado para
`FECHA_NACIMIENTO_FUTURA`.

## 8. Endpoints anidados: dos controllers nuevos + extensión de `PersonaController`

**Decision**:

- `PersonaController` (existente) se extiende con
  `POST/GET /api/personas/{id}/automoviles` (alta y listado de automóviles
  de una persona) — mismo patrón que `POST/GET /api/personas/{id}/profesiones`.
- `AutomovilController` (nuevo) expone
  `GET/PATCH/DELETE /api/automoviles/{id}`, `POST /api/automoviles/{id}/restaurar`,
  y `POST/GET /api/automoviles/{id}/mantenimientos` (registrar y listar el
  historial de mantenimientos de ese automóvil).
- `MantenimientoController` (nuevo) expone
  `GET/PATCH/DELETE /api/mantenimientos/{id}` y
  `POST /api/mantenimientos/{id}/restaurar`.

**Rationale**: Sigue el mismo criterio de `007-profesiones-personas` (donde
`PersonaController` hospeda solo alta/listado del sub-recurso, y
`ProfesionController` hospeda las operaciones "sobre sí mismo" del recurso
padre del sub-recurso, incluyendo su propio directorio anidado). Aquí hay un
nivel adicional (Persona → Automóvil → Mantenimiento), así que se repite el
mismo criterio un nivel más: cada controller hospeda las operaciones sobre
su propio recurso más el alta/listado del hijo inmediato.

**Alternatives considered**: Un único `AutomovilController` con todas las
rutas de mantenimiento incluidas (`/automoviles/{id}/mantenimientos/{mid}`)
en vez de un `MantenimientoController` separado — rechazada: el mantenimiento
tiene 4 operaciones propias completas (detalle, editar, baja, restaurar) que
ya justifican su propio controller, igual que `PersonaProfesion` no tiene
controller propio porque solo tiene 1 operación propia (retirar) frente a
las 4+ de `Mantenimiento`.

## 9. DTO del mecánico en la respuesta: proyección mínima, nunca la entidad completa

**Decision**: `MantenimientoResponseDTO` incluye un campo `mecanico` de tipo
`MecanicoResumenDTO(UUID id, String nombreCompleto)` cuando aplica, nunca la
entidad `Persona` completa ni un DTO con correo/teléfono/CURP/RFC/dirección.

**Rationale**: Principio IV (Privacidad por Diseño) y precedente directo de
`PersonaDirectorioDTO` en 007 (directorio de profesión: solo id + nombre
completo + datos propios de la asignación, nunca los demás datos
personales).

**Alternatives considered**: Reusar `PersonaResponseDTO` completo — rechazado
por exponer datos personales sin necesidad (correo, teléfono, CURP, RFC).

## 10. Piezas y mecánico en la creación: una sola transacción

**Decision**: `POST /api/automoviles/{id}/mantenimientos` recibe el
mantenimiento y su lista de piezas en un único `MantenimientoRequestDTO`,
persistidos en una única transacción `@Transactional` del service (crear
`Mantenimiento`, asociar sus `PiezaCambiada` vía cascada, validar mecánico y
kilometraje antes del `save`, registrar la entrada de historial).

**Rationale**: Pedido explícito del usuario ("en una sola operación"); evita
estados intermedios inconsistentes (un mantenimiento sin piezas si la
petición se corta a medias) y es el patrón ya usado para
"persona + dirección" en `POST /api/personas` (una sola transacción para
ambas entidades relacionadas).

## 11. Testcontainers para los tests de repositorio

**Decision**: `AutomovilRepositoryIT` y `MantenimientoRepositoryIT` (y,
implícitamente, las inserciones de `PiezaCambiada` vía cascada) usan el mismo
`AbstractIntegrationTest` (contenedor PostgreSQL 16 singleton) ya establecido
en el proyecto, no H2.

**Rationale**: Los índices únicos parciales (`placas WHERE activo = true`) y
el `CHECK` de kilometraje/costo/año son sintaxis específica de PostgreSQL
que H2 no reproduce fielmente (mismo motivo documentado en 007 para
`unaccent`).

## 12. Reutilización de excepciones: mismo criterio que 007

**Decision**: Se reutiliza `RecursoNoEncontradoException` para los 404
(`AUTOMOVIL_NO_ENCONTRADO`, `MANTENIMIENTO_NO_ENCONTRADO`) y
`DuplicateFieldException` para los 409 de duplicidad
(`AUTOMOVIL_PLACAS_DUPLICADAS`, `AUTOMOVIL_VIN_DUPLICADO`). Se crean clases
nuevas, simples (un solo constructor `String mensaje`, sin lógica), solo
para los 409/400 genuinamente nuevos sin una excepción reutilizable
existente: `AutomovilEliminadoException` (`AUTOMOVIL_ELIMINADO` — también
cubre el caso de registrar/editar un mantenimiento sobre un automóvil dado
de baja), `AutomovilYaActivoException`, `MantenimientoYaActivoException`,
`KilometrajeInconsistenteException`, `MecanicoNoEncontradoException` (400),
`MecanicoEliminadoException` (409), `MecanicoSinProfesionActivaException`
(409). No se necesita una `MantenimientoEliminadoException` separada: no hay
ningún escenario en el que el estado `activo=false` de un mantenimiento
bloquee otra operación (a diferencia de un automóvil, cuyo estado sí
bloquea el registro de mantenimientos nuevos).

**Rationale**: Mismo criterio ya aplicado (y documentado como corrección de
`tasks.md`) en `007-profesiones-personas`: no crear una clase de excepción
por cada `ErrorCode` cuando una genérica ya existente cubre el caso.

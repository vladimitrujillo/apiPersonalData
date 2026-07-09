# Feature Specification: Automóviles de Personas y Mantenimientos

**Feature Branch**: `008-automoviles-mantenimientos`

**Created**: 2026-07-08

**Status**: Draft

## Clarifications

### Session 2026-07-08

- Q: FR-025: un mantenimiento eliminado lógicamente por un ADMIN, ¿se puede
  restaurar más adelante (endpoint propio, análogo a personas y
  automóviles), o su eliminación es definitiva en esta primera versión? →
  A: Sí, se agrega restaurar — mismo patrón que persona y automóvil; ya que
  los mantenimientos ahora son editables (no un log inmutable), la
  restauración encaja con el resto del sistema.
- Q: FR-017: la regla de consistencia de kilometraje dice que un
  mantenimiento nuevo/editado no puede tener un kilometraje menor al del
  "mantenimiento más reciente (por fecha)". Dado que una fecha pasada
  (backfill) sí está permitida (FR-014 solo prohíbe fechas futuras) y los
  mantenimientos no siempre se capturan en orden cronológico, ¿contra qué
  se compara exactamente el kilometraje? → A: Únicamente contra el
  kilometraje del mantenimiento activo con la fecha más reciente de todo
  el automóvil, sin importar la fecha del registro nuevo/editado ni su
  posición cronológica relativa. El caso de backfill con fecha pasada
  queda fuera de alcance de esta validación en esta primera versión.

**Input**: User description: "Agregar un módulo de automóviles y sus mantenimientos. Automóvil: pertenece a una persona (una persona puede tener varios). Datos: marca, modelo, año, color, placas (única entre activos), VIN (único global, opcional), estado activo (borrado lógico igual que persona). Mantenimiento: pertenece a un automóvil. Datos: descripción, fecha (no futura), kilometraje (entero >= 0), mecánico (referencia opcional a una persona registrada), costo total (MXN, >= 0), y cero o más piezas cambiadas. Pieza cambiada: pertenece a un mantenimiento; nombre, número de parte (opcional), costo individual (opcional, >= 0). CRUD de automóviles bajo la persona (crear, consultar, listar los de una persona, actualizar, eliminación lógica; restauración solo ADMIN). Registrar mantenimiento con sus piezas en una sola operación; historial paginado ordenado por fecha descendente; consultar un mantenimiento por ID con sus piezas y datos básicos del mecánico; actualizar y eliminar (lógico) un mantenimiento. Reglas: fecha no futura, costos no negativos, año 1900 a año actual+1, kilometraje entero >= 0, consistencia de kilometraje contra el mantenimiento más reciente del automóvil, validación del mecánico (persona activa con la profesión Mecánico activa) al registrar/modificar sin afectar retroactivamente el historial, VIN único global (análogo a CURP), placas únicas solo entre activos y reasignables (análogo a correo). Permisos: CAPTURISTA crea/consulta/actualiza; solo ADMIN elimina y restaura. Auditoría con el mecanismo existente (historial con diff)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar un automóvil a una persona (Priority: P1)

Un operador (ADMIN o CAPTURISTA) da de alta un automóvil nuevo asociado a una persona activa, capturando marca, modelo, año, color, placas y, opcionalmente, el VIN.

**Why this priority**: Es la base de todo el feature — sin poder registrar un automóvil no hay nada que mantener ni consultar.

**Independent Test**: Puede probarse por completo dando de alta un automóvil a una persona existente y confirmando que la operación responde éxito con los datos capturados, sin depender de mantenimientos ni de otras historias.

**Acceptance Scenarios**:

1. **Given** una persona activa sin automóviles, **When** un operador registra un automóvil con marca, modelo, año, color y placas, **Then** el automóvil queda activo y asociado a esa persona.
2. **Given** un automóvil activo con placas "ABC-123", **When** un operador registra otro automóvil nuevo con las mismas placas, **Then** el sistema rechaza el alta por duplicidad.
3. **Given** un automóvil (activo o eliminado) con VIN "1HGCM82633A004352", **When** un operador registra un automóvil nuevo con ese mismo VIN, **Then** el sistema rechaza el alta por duplicidad, sin importar el estado del automóvil existente.
4. **Given** una persona eliminada lógicamente, **When** un operador intenta registrarle un automóvil, **Then** el sistema rechaza el alta.
5. **Given** un año de automóvil fuera del rango 1900–(año actual + 1), **When** un operador intenta registrar el automóvil, **Then** el sistema rechaza el alta indicando el campo inválido.

---

### User Story 2 - Consultar los automóviles de una persona (Priority: P1)

Un operador consulta el listado de automóviles registrados a nombre de una persona, y el detalle de uno en particular.

**Why this priority**: Sin poder consultar lo registrado, el alta no tiene valor operativo; es la contraparte mínima de la historia 1.

**Independent Test**: Puede probarse registrando automóviles a una persona y confirmando que el listado y el detalle regresan exactamente los datos capturados, sin depender de mantenimientos.

**Acceptance Scenarios**:

1. **Given** una persona con dos automóviles activos registrados, **When** un operador consulta su listado de automóviles, **Then** la respuesta incluye ambos.
2. **Given** un automóvil existente, **When** un operador consulta su detalle, **Then** la respuesta incluye marca, modelo, año, color, placas, VIN y estado.
3. **Given** una persona sin automóviles, **When** un operador consulta su listado, **Then** la respuesta es una lista vacía (no un error).

---

### User Story 3 - Registrar un mantenimiento con sus piezas (Priority: P1)

Un operador registra, en una sola operación, un mantenimiento realizado a un automóvil junto con las piezas cambiadas durante el servicio, y opcionalmente el mecánico que lo realizó.

**Why this priority**: Es el segundo propósito central del feature; habilita construir el historial que da valor a todo lo demás.

**Independent Test**: Puede probarse registrando un mantenimiento (con y sin piezas/mecánico) sobre un automóvil ya existente y confirmando que la operación responde éxito con todos los datos capturados.

**Acceptance Scenarios**:

1. **Given** un automóvil activo perteneciente a una persona activa, **When** un operador registra un mantenimiento con descripción, fecha, kilometraje, costo total, dos piezas cambiadas y un mecánico que es persona activa con la profesión "Mecánico" asignada de forma activa, **Then** el mantenimiento queda registrado con sus piezas y el nombre del mecánico visible.
2. **Given** las mismas condiciones, **When** un operador registra un mantenimiento sin piezas ni mecánico, **Then** el mantenimiento queda registrado igualmente.
3. **Given** un automóvil activo, **When** un operador intenta registrar un mantenimiento con fecha futura, costo total negativo, costo de una pieza negativo, o kilometraje negativo, **Then** el sistema rechaza el registro indicando el campo inválido en cada caso.
4. **Given** un automóvil cuyo mantenimiento más reciente (por fecha) tiene un kilometraje registrado, **When** un operador intenta registrar un mantenimiento nuevo con un kilometraje menor, **Then** el sistema rechaza el registro indicando el kilometraje y la fecha del mantenimiento que lo contradice.
5. **Given** un identificador de mecánico que no corresponde a ninguna persona registrada, **When** un operador intenta registrar el mantenimiento, **Then** el sistema rechaza el registro.
6. **Given** una persona eliminada lógicamente referenciada como mecánico, **When** un operador intenta registrar el mantenimiento, **Then** el sistema rechaza el registro indicando que la persona está eliminada.
7. **Given** una persona activa sin la profesión "Mecánico" asignada de forma activa, referenciada como mecánico, **When** un operador intenta registrar el mantenimiento, **Then** el sistema rechaza el registro indicando que la persona no está registrada como mecánico.
8. **Given** un automóvil eliminado lógicamente, **When** un operador intenta registrarle un mantenimiento, **Then** el sistema rechaza el registro.
9. **Given** un automóvil perteneciente a una persona eliminada lógicamente, **When** un operador intenta registrarle un mantenimiento, **Then** el sistema rechaza el registro.

---

### User Story 4 - Consultar el historial y el detalle de mantenimientos (Priority: P1)

Un operador consulta, de forma paginada y ordenada por fecha descendente, todos los mantenimientos de un automóvil, y el detalle completo de uno en particular junto con sus piezas y los datos básicos del mecánico.

**Why this priority**: Es la contraparte de consulta de la historia 3; sin ella el registro de mantenimientos no tiene valor operativo.

**Independent Test**: Puede probarse registrando varios mantenimientos a un automóvil y confirmando que el historial y el detalle regresan exactamente lo capturado, en el orden correcto.

**Acceptance Scenarios**:

1. **Given** un automóvil con tres mantenimientos registrados en fechas distintas, **When** un operador consulta su historial, **Then** la respuesta incluye los tres, ordenados de la fecha más reciente a la más antigua.
2. **Given** un mantenimiento con piezas y mecánico registrados, **When** un operador consulta su detalle por ID, **Then** la respuesta incluye sus piezas y el id y nombre completo del mecánico, sin el resto de sus datos personales.
3. **Given** un mecánico al que después se le retira la profesión "Mecánico" o se le elimina lógicamente, **When** un operador consulta un mantenimiento ya registrado con ese mecánico, **Then** el mantenimiento sigue mostrando su nombre sin cambios.
4. **Given** un automóvil sin mantenimientos registrados, **When** un operador consulta su historial, **Then** la respuesta es una página vacía (no un error).

---

### User Story 5 - Actualizar los datos de un automóvil (Priority: P2)

Un operador corrige o actualiza marca, modelo, año, color o placas de un automóvil ya registrado.

**Why this priority**: Es una operación de mantenimiento de datos secundaria frente al flujo central de alta/mantenimientos/consultas.

**Independent Test**: Puede probarse editando un automóvil existente y confirmando que los nuevos valores se reflejan en su detalle.

**Acceptance Scenarios**:

1. **Given** un automóvil activo, **When** un operador edita su marca, modelo, año, color o placas, **Then** el detalle del automóvil refleja los nuevos valores.
2. **Given** un automóvil activo, **When** un operador intenta editar su VIN, **Then** el sistema no permite cambiarlo (el VIN es inmutable tras el alta).
3. **Given** un automóvil activo, **When** un operador intenta editar sus placas a unas que ya están activas en otro automóvil, **Then** el sistema rechaza la edición por duplicidad.

---

### User Story 6 - Actualizar, eliminar y restaurar un mantenimiento (Priority: P2)

Un operador corrige un mantenimiento ya registrado; un ADMIN lo elimina lógicamente si fue capturado por error, y puede restaurarlo más adelante.

**Why this priority**: Cierra el ciclo de captura de datos de mantenimientos; es menos frecuente que su registro y consulta inicial.

**Independent Test**: Puede probarse editando un mantenimiento existente y confirmando que el cambio se refleja; eliminándolo lógicamente y confirmando que deja de aparecer en el historial; y restaurándolo y confirmando que reaparece intacto.

**Acceptance Scenarios**:

1. **Given** un mantenimiento existente, **When** un operador actualiza su descripción, costo o piezas, **Then** el detalle del mantenimiento refleja los nuevos valores, aplicando las mismas validaciones que al registrarlo (fecha no futura, costos no negativos, kilometraje consistente, mecánico válido si se cambia).
2. **Given** un mantenimiento existente, **When** un ADMIN lo elimina lógicamente, **Then** deja de aparecer en el historial del automóvil.
3. **Given** un mantenimiento existente, **When** un CAPTURISTA intenta eliminarlo lógicamente, **Then** el sistema rechaza la operación.
4. **Given** un mantenimiento eliminado lógicamente, **When** un ADMIN lo restaura, **Then** vuelve a aparecer en el historial del automóvil con sus datos y piezas intactos.
5. **Given** un mantenimiento eliminado lógicamente, **When** un CAPTURISTA intenta restaurarlo, **Then** el sistema rechaza la operación.

---

### User Story 7 - Eliminar y restaurar un automóvil (Priority: P2)

Un ADMIN da de baja lógicamente un automóvil que ya no pertenece a la persona o fue registrado por error, y puede restaurarlo más adelante.

**Why this priority**: Es una operación de cierre de ciclo de vida con permisos más restringidos; el sistema entrega valor operativo sin ella durante el uso inicial.

**Independent Test**: Puede probarse eliminando lógicamente un automóvil con mantenimientos previos, confirmando que tanto él como su historial dejan de ser consultables, y luego restaurándolo y confirmando que ambos reaparecen intactos.

**Acceptance Scenarios**:

1. **Given** un automóvil activo con mantenimientos previos, **When** un ADMIN lo elimina lógicamente, **Then** el automóvil y todo su historial de mantenimientos dejan de aparecer en consultas y listados.
2. **Given** un automóvil activo, **When** un CAPTURISTA intenta eliminarlo lógicamente, **Then** el sistema rechaza la operación.
3. **Given** un automóvil eliminado lógicamente con mantenimientos previos, **When** un ADMIN lo restaura, **Then** el automóvil y su historial de mantenimientos vuelven a ser consultables, intactos.
4. **Given** un automóvil eliminado lógicamente, **When** un CAPTURISTA intenta restaurarlo, **Then** el sistema rechaza la operación.

---

### Edge Cases

- Placas iguales a las de un automóvil activo existente se rechazan por duplicidad; placas de un automóvil eliminado lógicamente se pueden reutilizar en un automóvil nuevo.
- Un VIN igual al de cualquier otro automóvil, esté activo o eliminado lógicamente, siempre se rechaza por duplicidad (identidad única del vehículo).
- Eliminar lógicamente un automóvil oculta también todos sus mantenimientos (y las piezas de estos) de cualquier consulta o listado; restaurarlo los vuelve a mostrar exactamente como estaban.
- Eliminar lógicamente un mantenimiento no afecta al automóvil ni a los demás mantenimientos de su historial.
- Retirarle la profesión "Mecánico" a una persona, o eliminarla lógicamente, después de haber sido registrada como mecánico en uno o más mantenimientos, no oculta ni modifica esos mantenimientos: su nombre permanece visible.
- Si un mecánico eliminado lógicamente cumple ambas condiciones de rechazo (está eliminado y ya no tiene la profesión "Mecánico" activa), el sistema prioriza indicar que la persona está eliminada.
- Dos mantenimientos del mismo automóvil registrados con la misma fecha: para la validación de kilometraje, se considera "más reciente" el que fue registrado más recientemente en el sistema.
- Registrar un mantenimiento con una fecha pasada anterior a la del mantenimiento activo más reciente del automóvil (backfill) sigue comparando su kilometraje únicamente contra el de ese mantenimiento más reciente por fecha, no contra el mantenimiento cronológicamente adyacente; esto puede rechazar (o aceptar) un backfill sin verificar consistencia contra el resto de la línea de tiempo, lo cual queda fuera de alcance en esta primera versión.

## Requirements *(mandatory)*

### Functional Requirements

**Automóviles**

- **FR-001**: El sistema DEBE permitir a un operador (ADMIN o CAPTURISTA) registrar un automóvil nuevo asociado a una persona activa, capturando marca, modelo, año, color, placas y, opcionalmente, VIN.
- **FR-002**: El sistema DEBE rechazar el registro o la edición de un automóvil cuyo año esté fuera del rango 1900 al año actual más uno.
- **FR-003**: El sistema NUNCA DEBE permitir dos automóviles activos con las mismas placas.
- **FR-004**: El sistema DEBE permitir registrar un automóvil con placas que ya pertenecen a un automóvil eliminado lógicamente, sin afectar a este último.
- **FR-005**: El sistema NUNCA DEBE permitir dos automóviles con el mismo VIN, sin importar el estado (activo o eliminado lógicamente) del automóvil existente.
- **FR-006**: El sistema DEBE rechazar el registro de un automóvil para una persona eliminada lógicamente.
- **FR-007**: El sistema DEBE permitir consultar el listado de automóviles de una persona y el detalle de un automóvil por su identificador.
- **FR-008**: El sistema DEBE permitir a un operador editar marca, modelo, año, color y placas de un automóvil activo, revalidando en cada edición las mismas reglas de unicidad y rango que al registrarlo. El VIN NUNCA se puede modificar tras el alta.
- **FR-009**: El sistema DEBE permitir a un ADMIN eliminar lógicamente un automóvil activo.
- **FR-010**: Eliminar lógicamente un automóvil DEBE ocultar también su historial de mantenimientos de cualquier consulta o listado, sin borrarlo.
- **FR-011**: El sistema DEBE permitir a un ADMIN restaurar un automóvil eliminado lógicamente, devolviéndolo junto con su historial de mantenimientos a su estado consultable previo.

**Mantenimientos**

- **FR-012**: El sistema DEBE permitir a un operador registrar, en una sola operación, un mantenimiento nuevo sobre un automóvil activo perteneciente a una persona activa, junto con cero o más piezas cambiadas, capturando descripción, fecha, kilometraje, costo total y, opcionalmente, el mecánico que lo realizó.
- **FR-013**: El sistema DEBE rechazar el registro de un mantenimiento sobre un automóvil eliminado lógicamente, o cuya persona dueña esté eliminada lógicamente, indicando la causa.
- **FR-014**: La fecha de un mantenimiento NUNCA DEBE ser posterior a la fecha actual.
- **FR-015**: El costo total del mantenimiento y el costo individual de cada pieza (cuando se indique) DEBEN ser mayores o iguales a cero.
- **FR-016**: El kilometraje de un mantenimiento DEBE ser un entero mayor o igual a cero.
- **FR-017**: El sistema NUNCA DEBE permitir registrar (o editar) un mantenimiento cuyo kilometraje sea menor al del mantenimiento activo con la fecha más reciente de todo el automóvil (sin importar la fecha del registro nuevo o editado, ni su posición cronológica relativa); al rechazarlo, DEBE indicar el kilometraje y la fecha del registro que lo contradice.
- **FR-018**: Cuando se referencie un mecánico, el sistema DEBE validar que sea una persona activa con la profesión "Mecánico" asignada de forma activa en el momento del registro o la edición del mantenimiento.
- **FR-019**: El sistema DEBE rechazar la referencia a un mecánico que no corresponde a ninguna persona registrada.
- **FR-020**: El sistema DEBE rechazar la referencia a un mecánico eliminado lógicamente, o a uno sin la profesión "Mecánico" asignada de forma activa, indicando en cada caso cuál de las dos condiciones no se cumple.
- **FR-021**: Cambios posteriores al estado del mecánico (retirarle la profesión "Mecánico", eliminarlo lógicamente) NUNCA DEBEN alterar, ocultar, ni invalidar mantenimientos ya registrados: su nombre DEBE permanecer visible en el historial del automóvil.
- **FR-022**: El sistema DEBE permitir consultar, de forma paginada y ordenada de la fecha más reciente a la más antigua, el historial de mantenimientos de un automóvil.
- **FR-023**: El sistema DEBE permitir consultar el detalle de un mantenimiento por su identificador, incluyendo sus piezas cambiadas y, si tiene mecánico asignado, el identificador y nombre completo de este (sin el resto de sus datos personales).
- **FR-024**: El sistema DEBE permitir a un operador actualizar un mantenimiento existente, aplicando las mismas validaciones que a su registro (fecha, costos, kilometraje, mecánico), incluyendo el rechazo si el automóvil o la persona dueña de este están eliminados lógicamente (mismo criterio que FR-013).
- **FR-025**: El sistema DEBE permitir a un ADMIN eliminar lógicamente un mantenimiento existente.
- **FR-025a**: El sistema DEBE permitir a un ADMIN restaurar un mantenimiento eliminado lógicamente, devolviéndolo a su estado consultable previo dentro del historial de su automóvil.

**Permisos y auditoría**

- **FR-026**: Registrar, consultar y actualizar automóviles y mantenimientos DEBE estar permitido a ADMIN y CAPTURISTA por igual.
- **FR-027**: Eliminar lógicamente y restaurar automóviles, y eliminar lógicamente y restaurar mantenimientos, DEBE estar restringido a ADMIN.
- **FR-028**: Toda alta, edición, eliminación lógica y restauración de un automóvil, y todo registro, edición y eliminación lógica de un mantenimiento, DEBE quedar registrada en el mecanismo de auditoría ya existente del sistema (quién, cuándo y qué cambió).
- **FR-029**: La suite de tests automatizados existente DEBE seguir pasando en su totalidad tras este cambio.

### Key Entities *(include if feature involves data)*

- **Automóvil**: pertenece a exactamente una Persona (una Persona puede tener varios). Atributos: marca, modelo, año, color, placas (únicas entre automóviles activos, editable, reutilizable tras baja), VIN (único global entre todos los automóviles, opcional, inmutable tras el alta), estado (activo/eliminado lógicamente).
- **Mantenimiento**: pertenece a exactamente un Automóvil (un Automóvil puede tener varios, a lo largo del tiempo). Atributos: descripción, fecha (nunca futura), kilometraje (entero, consistente con el histórico del automóvil), costo total (MXN, no negativo), estado (activo/eliminado lógicamente). Puede referenciar opcionalmente a una Persona como mecánico. Contiene cero o más Piezas cambiadas.
- **Pieza cambiada**: pertenece a exactamente un Mantenimiento. Atributos: nombre, número de parte (opcional), costo individual (opcional, no negativo).
- **Persona**: entidad ya existente del padrón; pasa a tener cero, una o varias relaciones como dueña de Automóviles, y puede ser referenciada como mecánico en Mantenimientos cuando tiene la profesión "Mecánico" asignada de forma activa (`007-profesiones-personas`).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Al registrar una persona, un automóvil y dos mantenimientos (uno con piezas y mecánico activo, otro sin ninguno de los dos), consultar el historial del automóvil regresa el 100% de los datos capturados: piezas, costos, kilometraje y nombre del mecánico donde aplica.
- **SC-002**: El 100% de los intentos de registrar un mantenimiento con fecha futura, costo negativo o kilometraje negativo es rechazado, identificando el campo inválido en cada caso.
- **SC-003**: El 100% de los intentos de registrar un mantenimiento con un kilometraje menor al del más reciente ya registrado en el mismo automóvil es rechazado, indicando el kilometraje y la fecha en conflicto.
- **SC-004**: El 100% de los intentos de registrar un mantenimiento con un mecánico inexistente, eliminado lógicamente, o sin la profesión "Mecánico" activa, es rechazado con una indicación clara de la causa.
- **SC-005**: El 100% de los mantenimientos que referencian a un mecánico permanecen sin cambios en su historial después de que a ese mecánico se le retire la profesión "Mecánico" o se le elimine lógicamente.
- **SC-006**: El 100% de los intentos de registrar un mantenimiento sobre un automóvil eliminado lógicamente, o cuya persona dueña esté eliminada lógicamente, es rechazado.
- **SC-007**: El 100% de los intentos de registrar un automóvil con un VIN ya usado por cualquier otro automóvil es rechazado; el 100% de los intentos de reutilizar las placas de un automóvil eliminado lógicamente en uno nuevo es aceptado.
- **SC-008**: El 100% de los intentos de un operador con rol CAPTURISTA de eliminar o restaurar un automóvil, o de eliminar o restaurar un mantenimiento, es rechazado.
- **SC-009**: La suite de tests automatizados existente mantiene una tasa de éxito del 100% tras este cambio.

## Assumptions

- El VIN es inmutable tras el alta del automóvil (identidad del vehículo, análogo a la CURP de una persona); las placas sí se pueden editar, revalidando en cada edición la unicidad entre automóviles activos (análogo al correo de una persona).
- Un automóvil o un mantenimiento eliminado lógicamente deja de ser consultable por su identificador directo y desaparece de los listados, con el mismo criterio ya usado para personas; no se agrega en esta versión un listado dedicado de "automóviles/mantenimientos eliminados" análogo a `GET /api/personas/eliminadas`, al no haberse solicitado explícitamente.
- Al actualizar un mantenimiento, si se envían piezas cambiadas, la lista enviada reemplaza por completo a la anterior (actualización de conjunto, no una fusión campo por campo).
- La regla de consistencia de kilometraje aplica tanto al registrar un mantenimiento nuevo como al actualizar uno existente, para evitar que una edición deje el historial del automóvil inconsistente.
- No existe restricción que impida que una persona sea el mecánico de sus propios automóviles.
- El costo total y el costo de cada pieza son montos numéricos simples en pesos mexicanos (MXN), sin conversión de divisas ni validación adicional de formato.
- No se contempla en esta primera versión eliminar físicamente un automóvil, un mantenimiento ni una pieza cambiada; todos siguen el mismo patrón de borrado lógico ya usado en el resto del sistema.

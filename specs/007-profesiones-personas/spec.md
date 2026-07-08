# Feature Specification: Catálogo de Profesiones y Asignación a Personas

**Feature Branch**: `007-profesiones-personas`

**Created**: 2026-07-08

**Status**: Draft

**Input**: User description: "Agregar un catálogo de profesiones y la capacidad de asignar profesiones a las personas registradas. Catálogo de profesiones: nombre (único, insensible a mayúsculas y acentos), descripción (opcional) y estado activo, con semilla 'Mecánico'. Asignación persona-profesión: fecha desde, cédula o certificado (opcional), estado activo; una persona no puede tener la misma profesión asignada dos veces de forma activa. Consultas: profesiones de una persona, directorio de personas por profesión (paginado, solo activas), catálogo de profesiones. Gestión del catálogo solo ADMIN (crear, editar descripción, desactivar/reactivar); asignar/retirar CAPTURISTA y ADMIN; todas las consultas CAPTURISTA y ADMIN. Reglas de duplicidad, bloqueo de asignación sobre profesión desactivada o persona eliminada, retiro como desactivación (no borrado), efecto de borrado lógico/restauración de persona sobre los directorios, y auditoría del mecanismo existente."

## Clarifications

### Session 2026-07-08

- Q: Después de retirar la asignación de una profesión a una persona, si esa
  misma profesión se le vuelve a asignar más adelante, ¿el sistema crea una
  asignación nueva (episodio histórico separado) o reactiva la asignación
  retirada existente? → A: Crea una asignación nueva; la retirada anterior
  queda intacta como episodio histórico separado, cada una con su propia
  fecha desde y cédula.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Administrar el catálogo de profesiones (Priority: P1)

Como ADMIN, necesito poder crear profesiones nuevas en un catálogo, editar su
descripción, y desactivarlas o reactivarlas, para mantener una lista
controlada y consistente de profesiones válidas que los operadores puedan
asignar a las personas del padrón.

**Why this priority**: Es el prerrequisito duro de todo lo demás — ninguna
profesión puede asignarse a una persona si no existe antes en el catálogo. El
catálogo arranca con la profesión "Mecánico" precargada.

**Independent Test**: Se puede probar de forma completa creando una profesión
nueva como ADMIN, editando su descripción, desactivándola y reactivándola, y
confirmando en cada paso el estado del catálogo, sin depender de que existan
personas todavía.

**Acceptance Scenarios**:

1. **Given** un ADMIN autenticado, **When** crea la profesión "Electricista",
   **Then** la profesión queda disponible (activa) en el catálogo.
2. **Given** el catálogo ya tiene la profesión "Mecánico" (semilla), **When**
   un ADMIN intenta crear la profesión "mecanico" (sin acento, en minúsculas),
   **Then** el sistema rechaza la creación por duplicidad.
3. **Given** una profesión existente pero desactivada, **When** un ADMIN
   intenta crearla de nuevo con el mismo nombre, **Then** el sistema rechaza
   la creación e indica que la profesión existente puede reactivarse en su
   lugar.
4. **Given** una profesión activa con personas que ya la tienen asignada,
   **When** un ADMIN la desactiva, **Then** la profesión deja de poder
   asignarse a nuevas personas, pero las asignaciones ya existentes no se ven
   afectadas.
5. **Given** una profesión desactivada, **When** un ADMIN la reactiva,
   **Then** vuelve a estar disponible para asignarse a personas.
6. **Given** un operador con rol CAPTURISTA autenticado, **When** intenta
   crear, editar o desactivar una profesión del catálogo, **Then** el sistema
   responde que no tiene permiso para hacerlo.

---

### User Story 2 - Asignar una profesión a una persona (Priority: P1)

Como operador (ADMIN o CAPTURISTA), necesito poder asignar una o varias
profesiones del catálogo a una persona del padrón, indicando desde cuándo
ejerce esa profesión y, opcionalmente, su cédula o certificado, para reflejar
su situación laboral.

**Why this priority**: Es el punto central de valor del feature — sin poder
asignar profesiones a personas, el catálogo administrado en la Historia 1 no
tiene ningún efecto observable.

**Independent Test**: Con al menos una profesión activa en el catálogo, se
puede probar de forma completa asignándola a una persona existente y
confirmando la asignación, sin depender de las demás historias.

**Acceptance Scenarios**:

1. **Given** una persona activa y la profesión "Electricista" activa en el
   catálogo, **When** un operador se la asigna, **Then** la asignación queda
   registrada con fecha desde (hoy, si no se indica otra) y, si se
   proporcionó, su cédula o certificado.
2. **Given** una persona que ya tiene "Mecánico" asignada de forma activa,
   **When** se le asigna también "Electricista", **Then** ambas profesiones
   quedan asignadas a esa persona.
3. **Given** una persona con una profesión ya asignada de forma activa,
   **When** un operador intenta asignarle esa misma profesión de nuevo,
   **Then** el sistema rechaza la asignación duplicada.
4. **Given** una profesión desactivada en el catálogo, **When** un operador
   intenta asignarla a una persona, **Then** el sistema rechaza la
   asignación.
5. **Given** una persona eliminada lógicamente, **When** un operador intenta
   asignarle una profesión, **Then** el sistema rechaza la asignación.

---

### User Story 3 - Consultar las profesiones de una persona (Priority: P1)

Como operador (ADMIN o CAPTURISTA), al consultar a una persona necesito ver
qué profesiones tiene asignadas, con sus datos de asignación, para conocer su
situación laboral registrada.

**Why this priority**: Sin poder consultarlas, el dato capturado en la
Historia 2 no aporta ningún valor visible.

**Independent Test**: Con al menos una persona que ya tiene profesiones
asignadas, se puede probar de forma completa consultando su listado de
profesiones y confirmando que aparecen con sus datos, sin depender de poder
retirarlas todavía.

**Acceptance Scenarios**:

1. **Given** una persona con "Mecánico" y "Electricista" asignadas de forma
   activa, **When** un operador consulta sus profesiones, **Then** la
   respuesta incluye ambas, cada una con su fecha desde y su cédula o
   certificado si se registró.
2. **Given** una persona sin ninguna profesión asignada, **When** un operador
   consulta sus profesiones, **Then** la respuesta es una lista vacía (no un
   error).

---

### User Story 4 - Consultar el directorio de personas por profesión (Priority: P1)

Como operador (ADMIN o CAPTURISTA), necesito poder consultar el listado
paginado de personas que ejercen una profesión determinada (p. ej. un
"directorio de mecánicos"), viendo su identificador, nombre completo y los
datos de su asignación, sin exponer el resto de su información personal.

**Why this priority**: Es el otro caso de uso central del feature —
consultar "quién ejerce esta profesión" es el motivo de negocio para tener el
catálogo y las asignaciones en primer lugar.

**Independent Test**: Con al menos una persona activa con una asignación
activa de una profesión determinada, se puede probar de forma completa
consultando el directorio de esa profesión y confirmando que aparece, sin
depender de poder retirar asignaciones todavía.

**Acceptance Scenarios**:

1. **Given** una persona activa con la profesión "Mecánico" asignada de forma
   activa, **When** un operador consulta el directorio de "Mecánico",
   **Then** la persona aparece con su identificador, nombre completo, fecha
   desde y cédula o certificado (sin el resto de sus datos personales).
2. **Given** una persona eliminada lógicamente que tenía "Mecánico" asignada
   de forma activa, **When** un operador consulta el directorio de
   "Mecánico", **Then** esa persona no aparece.
3. **Given** un directorio con más resultados que el tamaño de una página,
   **When** un operador lo consulta, **Then** los resultados se entregan
   paginados.

---

### User Story 5 - Retirar la asignación de una profesión a una persona (Priority: P2)

Como operador (ADMIN o CAPTURISTA), necesito poder retirar una profesión que
ya no ejerce una persona, para que deje de aparecer en el directorio
correspondiente sin perder el registro histórico de que la tuvo asignada.

**Why this priority**: Depende de que ya existan asignaciones activas
(Historia 2); es un complemento natural del ciclo de vida de una asignación,
pero no bloquea el valor central de asignar y consultar.

**Independent Test**: Con una persona que ya tiene una profesión asignada de
forma activa, se puede probar de forma completa retirándosela y confirmando
que deja de aparecer en el directorio de esa profesión, sin depender de la
Historia 6.

**Acceptance Scenarios**:

1. **Given** una persona con "Mecánico" asignada de forma activa, **When** un
   operador retira esa asignación, **Then** la persona deja de aparecer en el
   directorio de "Mecánico", pero conserva el resto de sus profesiones
   asignadas.
2. **Given** una asignación retirada, **When** un ADMIN consulta el
   historial de asignaciones de esa persona, **Then** la asignación retirada
   sigue siendo consultable (no se borró).

---

### Edge Cases

- Un nombre de profesión que difiere de uno ya existente solo en
  mayúsculas/minúsculas o acentos (p. ej. "Mecánico" vs "mecanico") se
  considera el mismo nombre para efectos de duplicidad, tanto si el existente
  está activo como si está desactivado.
- Eliminar lógicamente a una persona la retira de inmediato de todos los
  directorios de profesión en los que aparecía, sin modificar sus
  asignaciones; al restaurarla, vuelve a aparecer en esos directorios con las
  mismas asignaciones que tenía.
- Desactivar una profesión del catálogo no afecta en nada a las asignaciones
  ya existentes de esa profesión (las personas que la tenían la conservan y
  siguen apareciendo en su directorio); solo impide que se creen asignaciones
  nuevas de esa profesión mientras esté desactivada.
- Reasignar a una persona una profesión que antes tuvo y le fue retirada
  crea una asignación nueva e independiente; no reactiva ni sobrescribe la
  asignación retirada anterior, que sigue siendo consultable como su propio
  episodio histórico.

## Requirements *(mandatory)*

### Functional Requirements

**Catálogo de profesiones**

- **FR-001**: El sistema DEBE mantener un catálogo de profesiones con nombre,
  descripción opcional y estado (activa/desactivada).
- **FR-002**: El catálogo DEBE arrancar con la profesión "Mecánico"
  precargada.
- **FR-003**: El sistema DEBE permitir a un ADMIN crear profesiones nuevas en
  el catálogo.
- **FR-004**: El sistema NUNCA DEBE permitir dos profesiones del catálogo con
  el mismo nombre, comparando sin distinguir mayúsculas/minúsculas ni
  acentos, sin importar si la existente está activa o desactivada.
- **FR-005**: Cuando se rechaza la creación de una profesión por existir ya
  desactivada, el sistema DEBE indicar que puede reactivarse en su lugar.
- **FR-006**: El sistema DEBE permitir a un ADMIN editar la descripción de
  una profesión existente.
- **FR-007**: El sistema DEBE permitir a un ADMIN desactivar una profesión
  activa, y reactivar una profesión desactivada.
- **FR-008**: Desactivar una profesión NUNCA DEBE eliminar ni modificar las
  asignaciones ya existentes de esa profesión.
- **FR-009**: El sistema DEBE rechazar cualquier intento de asignar a una
  persona una profesión que esté desactivada.
- **FR-010**: El sistema DEBE permitir a cualquier operador (ADMIN o
  CAPTURISTA) consultar el catálogo; por defecto solo profesiones activas, y
  a un ADMIN que lo solicite explícitamente, también las desactivadas.

**Asignación de profesiones a personas**

- **FR-011**: El sistema DEBE permitir a un operador (ADMIN o CAPTURISTA)
  asignar a una persona activa una profesión activa del catálogo.
- **FR-012**: Cada asignación DEBE registrar: la fecha desde la cual aplica
  (con hoy como valor por defecto si no se indica otra), y opcionalmente una
  cédula o certificado.
- **FR-013**: El sistema NUNCA DEBE permitir que una persona tenga la misma
  profesión asignada de forma activa más de una vez simultáneamente. Una
  persona SÍ puede tener varias asignaciones históricas (activas o
  retiradas) de la misma profesión a lo largo del tiempo: al reasignarle una
  profesión previamente retirada, el sistema DEBE crear una asignación nueva
  (con su propia fecha desde y cédula o certificado), dejando la retirada
  anterior intacta como episodio histórico separado.
- **FR-014**: El sistema DEBE rechazar cualquier intento de asignar una
  profesión a una persona que esté eliminada lógicamente.
- **FR-015**: El sistema DEBE permitir a un operador (ADMIN o CAPTURISTA)
  retirar una asignación activa; retirarla la marca como inactiva sin
  eliminar su registro.
- **FR-016**: El sistema DEBE permitir consultar las profesiones asignadas a
  una persona (activas por defecto), incluyendo los datos propios de cada
  asignación (fecha desde, cédula o certificado).
- **FR-017**: Un ADMIN DEBE poder consultar también las asignaciones
  retiradas (histórico) de una persona.

**Directorio por profesión**

- **FR-018**: El sistema DEBE permitir consultar, de forma paginada, el
  listado de personas activas con una asignación activa de una profesión
  determinada, mostrando identificador, nombre completo, y los datos de la
  asignación (fecha desde, cédula o certificado) — sin el resto de los datos
  personales de la persona.
- **FR-019**: Una persona eliminada lógicamente NUNCA DEBE aparecer en un
  directorio de profesión, incluso si su asignación sigue activa; al
  restaurarla, DEBE reaparecer.
- **FR-020**: Una asignación retirada NUNCA DEBE hacer aparecer a la persona
  en el directorio de esa profesión.

**Permisos y auditoría**

- **FR-021**: Crear, editar y desactivar/reactivar profesiones del catálogo
  DEBE estar restringido a ADMIN.
- **FR-022**: Asignar y retirar profesiones a personas DEBE estar permitido a
  ADMIN y CAPTURISTA.
- **FR-023**: Consultar el catálogo, las profesiones de una persona y el
  directorio por profesión DEBE estar permitido a ADMIN y CAPTURISTA.
- **FR-024**: Toda alta, edición, desactivación/reactivación de una profesión
  del catálogo, y toda asignación o retiro de una profesión a una persona,
  DEBE quedar registrada en el mecanismo de auditoría ya existente del
  sistema (quién y cuándo).
- **FR-025**: La suite de tests automatizados existente DEBE seguir pasando
  en su totalidad tras este cambio.

### Key Entities *(include if feature involves data)*

- **Profesión (catálogo)**: entrada del catálogo controlado de profesiones
  válidas. Atributos: nombre (único, comparación insensible a
  mayúsculas/acentos), descripción (opcional), estado (activa/desactivada).
  Administrada por ADMIN; consultable por cualquier operador.
- **Asignación Persona-Profesión**: relación entre una Persona y una entrada
  del catálogo de Profesión. Atributos: fecha desde (por defecto, la fecha de
  la asignación), cédula o certificado (opcional), estado (activa/retirada).
  Una persona puede tener varias asignaciones (una por profesión distinta, o
  varias históricas de la misma profesión en distintos periodos); una
  profesión puede estar asignada a muchas personas; una persona no puede
  tener dos asignaciones *activas* de la misma profesión al mismo tiempo,
  pero sí puede acumular episodios pasados (retirados) y una nueva vigente
  de la misma profesión.
- **Persona**: entidad ya existente del padrón (ver
  `001-personas-codigos-postales`); pasa a relacionarse con cero, una o
  varias Profesiones a través de Asignaciones.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Al crear una profesión nueva y asignarla junto con la semilla
  "Mecánico" a una persona, consultar sus profesiones regresa el 100% de las
  asignadas.
- **SC-002**: El 100% de los intentos de crear en el catálogo una profesión
  con un nombre ya existente (incluyendo variantes de mayúsculas/acentos, y
  sin importar si la existente está activa o desactivada) es rechazado.
- **SC-003**: El 100% de las personas activas con una asignación activa de
  una profesión determinada aparece en el directorio de esa profesión; tras
  retirárseles esa asignación, el 100% deja de aparecer.
- **SC-004**: El 100% de los intentos de asignar una profesión desactivada
  del catálogo a una persona es rechazado.
- **SC-005**: El 100% de los intentos de asignar una profesión a una persona
  eliminada lógicamente es rechazado.
- **SC-006**: El 100% de las personas eliminadas lógicamente desaparece de
  inmediato de los directorios de profesión en los que aparecía, y el 100%
  reaparece exactamente con las mismas asignaciones al restaurarse.
- **SC-007**: El 100% de los intentos de un CAPTURISTA de crear, editar o
  desactivar/reactivar profesiones del catálogo es rechazado, mientras que el
  100% de sus intentos de asignar o retirar profesiones a personas se
  procesa con normalidad.
- **SC-008**: La suite de tests automatizados existente mantiene una tasa de
  éxito del 100% después del cambio.

## Assumptions

- Asignar (y retirar) profesiones a una persona es un flujo propio,
  independiente del alta/edición general de la persona (no se agrega como
  parte de `PersonaRequestDTO`) — análogo a cómo ya funcionan hoy el
  historial de auditoría y la restauración de una persona
  (`GET /api/personas/{id}/historial`, `POST /api/personas/{id}/restaurar`),
  no al patrón de Dirección (que sí se embebe en el alta).
- El tamaño de página por defecto y máximo para el directorio por profesión y
  para el listado del catálogo siguen el mismo estándar ya usado en el resto
  del sistema (página 0 en adelante, tamaño por defecto 20, máximo 100).
- La cédula o certificado es un dato de texto libre sin un formato
  particular que validar (el usuario decide qué escribir ahí: número de
  cédula profesional, folio de certificado, etc.).
- No se contempla en esta primera versión eliminar físicamente una profesión
  del catálogo ni una asignación; ambas siguen el mismo patrón de borrado
  lógico/desactivación ya usado para personas en el resto del sistema.
- No es necesario buscar/filtrar personas combinando profesión con otros
  criterios de `005-busqueda-avanzada-personas` en esta primera versión; el
  directorio por profesión es una consulta independiente.

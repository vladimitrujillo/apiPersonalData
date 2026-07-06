# Feature Specification: Búsqueda Avanzada de Personas

**Feature Branch**: `005-busqueda-avanzada-personas`

**Created**: 2026-07-06

**Status**: Draft

**Input**: User description: "Agregar búsqueda avanzada de personas. Extiende el listado existente (paginación y filtros básicos) sin romperlo. Nuevos criterios combinables (todos opcionales): texto libre sobre nombres/apellidos insensible a mayúsculas/acentos, CURP parcial (prefijo), rango de edad, rango de fechas de registro, municipio y estado (existentes), sexo, estado activo/eliminado (solo ADMIN; CAPTURISTA siempre forzado a activo=true). Criterios combinados con AND. Ordenamiento por nombre, fecha de nacimiento o fecha de registro, asc/desc. Misma paginación y formato de respuesta. Parámetros inválidos (edad negativa, rango invertido, campo de orden desconocido): 400 con detalle por parámetro."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Encontrar personas por texto aunque no se escriban acentos (Priority: P1)

Como operador que busca a una persona por su nombre, necesito que escribir "jose" o
"garcia" encuentre a "José" o "García" sin que yo tenga que escribir los acentos
exactos, porque en el uso diario casi nadie los teclea.

**Why this priority**: Es el criterio de búsqueda más usado en el día a día y el que
ya existe parcialmente (coincidencia de texto); extenderlo a insensible-a-acentos es la
mejora de mayor impacto inmediato y la más simple de verificar de forma aislada.

**Independent Test**: Con una persona llamada "José García" ya registrada, se puede
verificar completamente buscando "jose" y "garcia" por separado (sin combinar con
ningún otro criterio) y confirmando que ambas búsquedas la encuentran.

**Acceptance Scenarios**:

1. **Given** una persona llamada "José García", **When** se busca por texto "jose",
   **Then** aparece en los resultados.
2. **Given** esa misma persona, **When** se busca por texto "garcia", **Then** aparece
   en los resultados (coincide por apellido, insensible a mayúsculas y acentos).
3. **Given** el listado existente, **When** se llama sin ningún parámetro nuevo de este
   feature, **Then** la respuesta es idéntica a la de hoy (mismo formato, mismo
   comportamiento — el contrato existente no se rompe).

---

### User Story 2 - Combinar varios criterios a la vez (Priority: P1)

Como operador, necesito poder acotar una búsqueda combinando texto, rango de edad,
estado (geográfico) y los demás criterios a la vez, para encontrar exactamente al grupo
de personas que busco sin tener que revisar manualmente una lista más amplia.

**Why this priority**: Es la razón de ser de "avanzada": una búsqueda que solo permite
un criterio a la vez ya existe (el listado actual). El valor nuevo está en la
combinación.

**Independent Test**: Con varias personas registradas con distintos nombres, edades y
estados (geográficos) y activos/eliminados, se puede verificar completamente
combinando texto + rango de edad + estado (activo/eliminado) en una sola llamada y
confirmando que el resultado es exactamente la intersección de los tres criterios.

**Acceptance Scenarios**:

1. **Given** varias personas con distintos nombres, edades y estado
   (activo/eliminado), **When** se busca combinando texto + rango de edad + estado
   (activo/eliminado), **Then** el resultado contiene únicamente a quienes cumplen los
   tres criterios a la vez (intersección, no unión).
2. **Given** cualquier combinación de los criterios nuevos (CURP parcial, rango de
   fechas de registro, municipio, estado geográfico, sexo), **When** se combinan entre
   sí, **Then** el resultado es igualmente la intersección de todos los criterios
   enviados.
3. **Given** un resultado de búsqueda combinada, **When** se solicita ordenado por
   nombre, fecha de nacimiento o fecha de registro (ascendente o descendente), **Then**
   el resultado respeta ese orden.

---

### User Story 3 - Un CAPTURISTA nunca ve personas eliminadas por esta vía (Priority: P1)

Como CAPTURISTA, cuando uso la búsqueda avanzada, necesito que nunca me aparezcan
personas eliminadas lógicamente, incluso si por error o intento envío un parámetro
pidiéndolas, para no verme expuesto a exigencias de permisos que no tengo ni a
confusión sobre qué registros están vigentes.

**Why this priority**: Es una regla de seguridad/privacidad (quién puede ver qué),
igual de crítica que la funcionalidad misma de búsqueda — sin ella, la búsqueda
avanzada abriría una vía no controlada para que un CAPTURISTA vea lo que
`GET /api/personas/eliminadas` (feature `004`) ya restringe explícitamente a ADMIN.

**Independent Test**: Con al menos una persona eliminada lógicamente, se puede
verificar completamente autenticando como CAPTURISTA, buscando con el parámetro de
estado activo/eliminado puesto explícitamente en "eliminado", y confirmando que la
persona eliminada NO aparece en el resultado (la búsqueda se comporta como si ese
parámetro no se hubiera enviado).

**Acceptance Scenarios**:

1. **Given** una persona eliminada lógicamente, **When** un CAPTURISTA busca sin
   especificar el criterio de estado activo/eliminado, **Then** esa persona no aparece
   (mismo comportamiento que hoy).
2. **Given** esa misma persona, **When** un CAPTURISTA busca especificando
   explícitamente que quiere ver eliminadas, **Then** esa persona sigue sin aparecer
   (el parámetro se ignora para su rol, sin error).
3. **Given** esa misma persona, **When** un ADMIN busca especificando que quiere ver
   eliminadas, **Then** esa persona sí aparece.

---

### Edge Cases

- Un rango de edad con el mínimo mayor que el máximo, o negativo, se rechaza con 400
  indicando el parámetro específico (no se ejecuta la búsqueda).
- Un rango de fechas de registro con "desde" posterior a "hasta" se rechaza con 400 de
  la misma forma.
- Un campo de ordenamiento no reconocido (fuera de nombre/fecha de nacimiento/fecha de
  registro) se rechaza con 400 indicando el parámetro.
- Buscar sin ningún criterio (todos opcionales) se comporta igual que el listado actual
  sin filtros: devuelve todas las personas activas paginadas (o todas, según el rol y
  el criterio de estado, ver US3).
- Una búsqueda por CURP parcial que no coincide con ningún prefijo devuelve una página
  vacía, no un error (mismo criterio que otros filtros existentes sin coincidencias).
- Combinar municipio/estado (geográfico) nuevos con los ya existentes no cambia su
  semántica actual (coincidencia parcial), solo se suman al resto de criterios con AND.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir buscar personas por texto libre sobre
  nombres y apellidos, de forma insensible a mayúsculas/minúsculas y a acentos/diacríticos,
  por coincidencia parcial (comportamiento ya existente, ampliado para ignorar acentos).
- **FR-002**: El sistema DEBE permitir buscar personas por un prefijo de CURP
  (coincidencia desde el inicio del valor, no en cualquier posición).
- **FR-003**: El sistema DEBE permitir acotar por un rango de edad (mínima, máxima, o
  ambas), calculada a partir de la fecha de nacimiento a la fecha en que se ejecuta la
  búsqueda.
- **FR-004**: El sistema DEBE permitir acotar por un rango de fechas de registro
  (desde/hasta), sobre la fecha en que la persona fue dada de alta.
- **FR-005**: El sistema DEBE conservar los criterios existentes de municipio y estado
  (geográfico) con su comportamiento actual (coincidencia parcial).
- **FR-006**: El sistema DEBE permitir acotar por sexo.
- **FR-007**: El sistema DEBE permitir acotar por estado activo/eliminado, con efecto
  únicamente para usuarios con rol ADMIN.
- **FR-008**: Para un usuario con rol CAPTURISTA, el sistema DEBE ignorar cualquier
  valor enviado en el criterio de estado activo/eliminado y comportarse siempre como si
  se hubiera solicitado únicamente activas, sin producir error.
- **FR-009**: Todos los criterios de este feature DEBEN ser opcionales y combinables
  entre sí; cuando se envía más de uno, el resultado DEBE ser la intersección (AND) de
  todos los criterios enviados, no la unión.
- **FR-010**: El sistema DEBE permitir ordenar el resultado por nombre, por fecha de
  nacimiento, o por fecha de registro, en dirección ascendente o descendente.
- **FR-011**: Cuando no se especifica ningún criterio de ordenamiento, el
  comportamiento DEBE ser idéntico al del listado existente hoy (sin este feature).
- **FR-012**: El sistema DEBE usar la misma paginación y el mismo formato de respuesta
  que el listado existente (sin cambios de schema).
- **FR-013**: Una llamada sin ningún parámetro nuevo de este feature DEBE responder de
  forma idéntica a como responde hoy el listado existente (contrato no roto).
- **FR-014**: El sistema DEBE rechazar con 400 (formato de error existente, con detalle
  por parámetro) cualquier rango de edad inválido (negativo, o mínimo mayor que
  máximo).
- **FR-015**: El sistema DEBE rechazar con 400 (mismo formato) cualquier rango de
  fechas de registro inválido (fecha "desde" posterior a "hasta").
- **FR-016**: El sistema DEBE rechazar con 400 (mismo formato) cualquier valor de
  criterio de ordenamiento que no sea uno de los reconocidos (nombre, fecha de
  nacimiento, fecha de registro).
- **FR-017**: La suite de tests automatizados existente DEBE seguir pasando en su
  totalidad tras este cambio.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una búsqueda de texto sin acentos ni mayúsculas exactas (p. ej. "jose",
  "garcia") encuentra al 100% de las personas cuyo nombre/apellido coincide
  independientemente de acentos o mayúsculas.
- **SC-002**: El 100% de las búsquedas que combinan varios criterios devuelve
  exactamente la intersección de esos criterios, nunca una unión más amplia.
- **SC-003**: El 100% de las llamadas al listado existente sin ningún parámetro nuevo
  responde de forma idéntica a como respondía antes de este feature.
- **SC-004**: El 100% de los intentos de un CAPTURISTA de ver personas eliminadas a
  través de la búsqueda avanzada, con o sin el parámetro correspondiente, no las
  incluye en el resultado.
- **SC-005**: El 100% de los parámetros inválidos (rango de edad, rango de fechas,
  campo de orden desconocido) es rechazado con un error que identifica el parámetro
  específico, sin ejecutar la búsqueda.
- **SC-006**: La suite de tests automatizados existente mantiene una tasa de éxito del
  100% después del cambio.

## Assumptions

- **Posible solapamiento con `004-restaurar-persona-curp`**: ese feature ya definió
  `GET /api/personas/eliminadas` como una vista **separada** del listado general,
  precisamente para que el listado general no tuviera ramas de comportamiento por rol
  (decisión tomada explícitamente en su `/speckit-clarify`). Este feature ahora pide lo
  contrario para el listado general: un criterio de estado activo/eliminado
  combinable con todos los demás filtros nuevos, con efecto solo para ADMIN. Ambas
  cosas pueden convivir (un endpoint simple de "ver eliminados" y una búsqueda
  avanzada combinable que también puede filtrar por estado), pero se deja constancia
  explícita para que, antes de planear, se decida si `GET /api/personas/eliminadas` de
  `004` sigue siendo necesario una vez que esta búsqueda avanzada pueda cubrir el mismo
  caso (y más), o si ambos se mantienen para usos distintos (uno simple, uno avanzado).
- El ordenamiento "por nombre" ordena por apellidos y luego nombres (el mismo criterio
  compuesto que ya usa la coincidencia de texto libre existente), no por un único
  campo de nombre de pila.
- El criterio de estado activo/eliminado admite tres valores posibles para ADMIN: solo
  activas (comportamiento por defecto, igual que hoy si se omite), solo eliminadas, o
  ambas a la vez; para CAPTURISTA siempre se comporta como "solo activas" (FR-008).
- Este feature depende de `002-autenticacion-autorizacion` (para distinguir ADMIN de
  CAPTURISTA en el criterio de estado); no depende de `003` ni de `004` para el resto
  de los criterios nuevos (texto, CURP, edad, fechas, municipio/estado, sexo), que
  aplican por igual a cualquier usuario autenticado que ya puede listar personas.
- No se especificaron límites de longitud/formato adicionales para el texto libre o el
  prefijo de CURP más allá de los ya validados en creación/actualización de personas;
  se asume que cualquier texto, incluso vacío o muy corto, es un criterio válido (un
  prefijo de CURP vacío simplemente no acota nada).

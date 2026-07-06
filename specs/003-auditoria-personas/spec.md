# Feature Specification: Auditoría y Historial de Cambios en Personas

**Feature Branch**: `003-auditoria-personas`

**Created**: 2026-07-06

**Status**: Draft

**Input**: User description: "Agregar auditoría sobre los datos de personas. Auditoría básica en persona y dirección (quién creó/modificó y cuándo, visible solo en consulta por ID). Historial de cambios inmutable: cada creación, modificación, eliminación lógica y restauración de una persona queda registrada con fecha/hora, usuario, tipo de operación y detalle de campos cambiados (valor anterior → nuevo); CURP, RFC y teléfono se registran enmascarados. Endpoint GET /personas/{id}/historial, paginado, solo ADMIN. Registrar el historial no debe fallar la operación principal: si falla, todo se revierte en la misma transacción. Los listados no incluyen datos de auditoría."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver quién y cuándo tocó una persona (Priority: P1)

Como usuario autenticado (ADMIN o CAPTURISTA) que consulta el detalle de una persona,
necesito ver quién la dio de alta y cuándo, y quién fue la última persona que la
modificó y cuándo, para poder rastrear responsabilidad sobre un registro sin tener que
pedirle ese dato a otra persona.

**Why this priority**: Es la pieza más simple y de mayor uso diario: cualquiera que
consulta un registro puede ver de inmediato su procedencia, sin depender del historial
completo (US2). No requiere la capacidad de restaurar (US3) para tener valor por sí
misma.

**Independent Test**: Con una persona ya creada y modificada por dos usuarios distintos,
se puede verificar completamente llamando a `GET /api/personas/{id}` y confirmando que
la respuesta incluye quién creó, cuándo, quién modificó por última vez y cuándo — tanto
para la persona como para su dirección. No depende de US2 ni de US3.

**Acceptance Scenarios**:

1. **Given** una persona creada por el usuario X, **When** se consulta por ID,
   **Then** la respuesta indica que X la creó y la fecha/hora de creación.
2. **Given** esa misma persona luego modificada por el usuario Y, **When** se consulta
   de nuevo por ID, **Then** la respuesta indica que Y fue quien la modificó por última
   vez y la fecha/hora de esa modificación (el creador original y su fecha no cambian).
3. **Given** cualquier persona, **When** se consulta el listado paginado de personas,
   **Then** la respuesta NO incluye quién creó/modificó ni las fechas correspondientes.

---

### User Story 2 - Consultar el historial completo de cambios (Priority: P1)

Como ADMIN, necesito poder ver la lista completa y en orden de todos los cambios que ha
sufrido una persona a lo largo del tiempo —qué se hizo, quién lo hizo, cuándo, y
exactamente qué campos cambiaron— para poder investigar cómo llegó un registro a su
estado actual o resolver una discrepancia reportada.

**Why this priority**: Es la razón de ser de la palabra "auditoría" en el feature: sin
el historial detallado, solo se sabe el último estado (US1), no cómo se llegó ahí. Es
igual de crítica que US1 porque ambas dependen de la misma captura de datos subyacente.

**Independent Test**: Con una persona creada por el usuario X y luego modificada por el
usuario Y, se puede verificar completamente autenticando como ADMIN, llamando a
`GET /api/personas/{id}/historial` y confirmando que aparecen ambas entradas con sus
campos cambiados. Autenticando como CAPTURISTA sobre la misma ruta se confirma el
rechazo. No depende de US3.

**Acceptance Scenarios**:

1. **Given** una persona creada por el usuario X y luego modificada por el usuario Y
   (cambiando, por ejemplo, el teléfono y la calle de su dirección), **When** un ADMIN
   consulta `GET /api/personas/{id}/historial`, **Then** obtiene una lista paginada con
   al menos dos entradas: una de creación (autor X) y una de modificación (autor Y) que
   detalla los campos cambiados con su valor anterior y nuevo.
2. **Given** un cambio que incluyó el CURP, el RFC o el teléfono, **When** se consulta el
   historial, **Then** esos valores (tanto el anterior como el nuevo) aparecen
   enmascarados, nunca en claro.
3. **Given** un usuario con rol CAPTURISTA autenticado, **When** intenta consultar
   `GET /api/personas/{id}/historial`, **Then** el sistema responde 403.
4. **Given** una persona eliminada lógicamente, **When** se consulta su historial,
   **Then** aparece una entrada de tipo eliminación con su autor y fecha/hora.

---

### User Story 3 - Restaurar una persona eliminada lógicamente (Priority: P2)

Como ADMIN, necesito poder revertir una eliminación lógica hecha por error, para que la
persona vuelva a aparecer en consultas y listados sin tener que volver a capturarla
desde cero (perdiendo su historial previo). Esta capacidad queda reservada a ADMIN,
igual que eliminar y que la gestión de usuarios.

**Why this priority**: Hoy el sistema no ofrece ninguna forma de deshacer una
eliminación lógica; esta historia añade esa capacidad porque el propio historial de
cambios (US2) debe poder mostrar una restauración como tipo de operación. Es P2 porque
US1 y US2 ya aportan valor de auditoría completo sobre creación/modificación/eliminación
sin que exista todavía una restauración.

**Independent Test**: Con una persona eliminada lógicamente, se puede verificar
completamente restaurándola y confirmando que vuelve a aparecer en `GET /api/personas/{id}`
y en el listado, y que su historial ahora incluye la entrada de restauración. No depende
de que exista ya una modificación previa (US1/US2 ya cubren eso por separado).

**Acceptance Scenarios**:

1. **Given** una persona eliminada lógicamente, **When** un ADMIN la restaura, **Then**
   vuelve a ser consultable por ID y a aparecer en el listado.
2. **Given** esa restauración, **When** se consulta el historial de la persona,
   **Then** aparece una entrada de tipo restauración con su autor y fecha/hora.
3. **Given** una persona eliminada lógicamente cuyo correo o CURP ya fue tomado por otra
   persona activa mientras estaba eliminada, **When** un ADMIN intenta restaurarla,
   **Then** el sistema rechaza la restauración con el mismo código de conflicto que usa
   la creación/actualización para correo/CURP duplicado.
4. **Given** un usuario con rol CAPTURISTA autenticado, **When** intenta restaurar una
   persona eliminada lógicamente, **Then** el sistema responde 403.

---

### Edge Cases

- Una persona creada antes de que este feature exista no tiene creador/modificador
  conocido ni entradas de historial previas a la fecha de despliegue del feature (ver
  Assumptions); su historial comienza a registrarse a partir de su primer cambio
  posterior al despliegue.
- Si falla el guardado de la entrada de historial por cualquier motivo, la operación que
  la originó (creación, modificación, eliminación lógica o restauración) se revierte por
  completo: no debe quedar un cambio en `persona`/`direccion` sin su entrada de
  historial correspondiente.
- Una modificación que solo cambia campos de la dirección (sin tocar campos propios de
  la persona) también genera una entrada de historial de tipo modificación, detallando
  los campos de dirección cambiados.
- Un intento de restaurar una persona que no está eliminada (ya está activa) se rechaza
  de forma explícita (no es una operación válida sobre un registro ya activo).
- Un intento de restaurar un `id` que no existe responde igual que cualquier operación
  sobre un `id` inexistente en este módulo (404).
- No existe ningún endpoint para editar o borrar entradas del historial; el historial es
  de solo lectura por diseño.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE registrar, para cada persona, quién la creó y en qué
  fecha/hora, y quién fue el último usuario en modificarla y en qué fecha/hora.
- **FR-002**: El sistema DEBE registrar el mismo tipo de datos de auditoría (creador,
  fecha de creación, último modificador, fecha de última modificación) para la
  dirección de cada persona.
- **FR-003**: La respuesta de `GET /api/personas/{id}` DEBE incluir los datos de
  auditoría de la persona y de su dirección.
- **FR-004**: Las respuestas de listados de personas (`GET /api/personas`) NO DEBEN
  incluir ningún dato de auditoría.
- **FR-005**: El sistema DEBE registrar una entrada de historial inmutable cada vez que
  una persona es creada, modificada, eliminada lógicamente, o restaurada.
- **FR-006**: Cada entrada de historial DEBE incluir: fecha/hora de la operación, el
  usuario que la realizó, el tipo de operación (creación, modificación, eliminación
  lógica, restauración), y — para modificaciones — la lista de campos cambiados con su
  valor anterior y su valor nuevo.
- **FR-007**: Cuando los campos cambiados en una entrada de historial incluyen CURP,
  RFC o teléfono, el sistema DEBE registrar esos valores (tanto el anterior como el
  nuevo) enmascarados, nunca en texto claro.
- **FR-008**: Los cambios a la dirección de una persona realizados como parte de una
  actualización DEBEN quedar reflejados como campos cambiados en la misma entrada de
  historial de esa operación.
- **FR-009**: El sistema NO DEBE exponer ningún endpoint para modificar o eliminar
  entradas de historial ya registradas.
- **FR-010**: Si el registro de la entrada de historial falla por cualquier motivo, el
  sistema DEBE revertir por completo la operación que la originó, de modo que ningún
  cambio en los datos de la persona o su dirección quede sin su entrada de historial
  correspondiente.
- **FR-011**: El sistema DEBE exponer `GET /api/personas/{id}/historial`, paginado, que
  devuelve las entradas de historial de esa persona ordenadas de la más reciente a la
  más antigua.
- **FR-012**: El sistema DEBE permitir el acceso a `GET /api/personas/{id}/historial`
  únicamente a usuarios con rol ADMIN, respondiendo 403 a cualquier otro rol
  autenticado, usando el formato de error ya existente en la API.
- **FR-013**: El sistema DEBE proveer una forma de restaurar una persona eliminada
  lógicamente, devolviéndola a un estado consultable y visible en listados, restringida
  únicamente a usuarios con rol ADMIN (mismo nivel de restricción que eliminar y que la
  gestión de usuarios); un intento de un usuario con rol CAPTURISTA DEBE rechazarse con
  403.
- **FR-014**: Un intento de restaurar una persona cuyo correo o CURP ya pertenece a otra
  persona actualmente activa DEBE rechazarse con el mismo código de conflicto usado hoy
  para correo/CURP duplicado.
- **FR-015**: Un intento de restaurar una persona que ya está activa, o cuyo `id` no
  existe, DEBE rechazarse de forma explícita y sin efecto sobre los datos.
- **FR-016**: La suite de tests automatizados existente DEBE seguir pasando en su
  totalidad tras este cambio.

### Key Entities *(include if feature involves data)*

- **Persona (ampliada)**: además de sus campos existentes, registra quién la creó,
  cuándo, quién la modificó por última vez, y cuándo.
- **Dirección (ampliada)**: mismo conjunto de datos de auditoría que Persona.
- **Entrada de Historial**: registro inmutable asociado a una persona. Atributos:
  persona a la que pertenece, fecha/hora, usuario autor, tipo de operación (creación,
  modificación, eliminación lógica, restauración), y — cuando aplica — la lista de
  campos cambiados (nombre de campo, valor anterior, valor nuevo), con CURP/RFC/teléfono
  siempre enmascarados dentro de esos valores.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las respuestas de `GET /api/personas/{id}` incluyen quién creó
  y quién modificó por última vez a la persona (y a su dirección), y cuándo.
- **SC-002**: El 100% de las respuestas de listado de personas excluyen esos datos de
  auditoría.
- **SC-003**: Es posible reconstruir, de punta a punta y para cualquier persona, la
  secuencia completa de quién hizo qué y cuándo (creación, cada modificación, eliminación
  lógica, restauración) consultando únicamente su historial.
- **SC-004**: El 100% de las apariciones de CURP, RFC o teléfono dentro del historial
  están enmascaradas.
- **SC-005**: El 100% de los intentos de un usuario con rol CAPTURISTA de consultar el
  historial de una persona son rechazados.
- **SC-006**: En pruebas que fuercen un fallo al guardar una entrada de historial, el
  100% de esos intentos deja los datos de la persona/dirección exactamente como estaban
  antes del intento (sin cambios parciales).
- **SC-007**: La suite de tests automatizados existente mantiene una tasa de éxito del
  100% después del cambio.

## Assumptions

- Este feature depende de que la identidad del usuario autenticado ya esté disponible en
  el contexto de seguridad (feature de autenticación y autorización, `002-autenticacion-autorizacion`).
  A la fecha de esta especificación, esa feature está especificada y planeada pero aún
  no implementada en el código; este feature no puede implementarse ni tener tasks
  ejecutables hasta que `002` esté implementado (o al menos su modelo de usuario y
  contexto de seguridad).
- Las personas y direcciones creadas antes de que este feature se despliegue no tienen
  creador/modificador conocido (se registran como desconocidos/nulos) ni entradas de
  historial previas; el historial comienza a capturarse a partir del primer cambio
  posterior al despliegue.
- Solo CURP, RFC y teléfono se enmascaran en el historial, tal como se especificó
  explícitamente; correo, nombres, apellidos, fecha de nacimiento, sexo y los campos de
  dirección se registran en claro.
- El formato exacto de enmascarado (p. ej. cuántos caracteres se muestran) es un detalle
  de presentación menor; se asume un enmascarado parcial estándar (se conserva una
  porción mínima reconocible y el resto se sustituye) siempre que nunca se exponga el
  valor completo.
- La paginación de `GET /api/personas/{id}/historial` sigue la misma convención ya usada
  por el listado de personas (página base 0, tamaño por defecto y máximo iguales).
- Los datos de auditoría (creador/modificador) en las respuestas de consulta por ID
  (US1) son visibles para cualquier rol que ya puede consultar personas (ADMIN y
  CAPTURISTA); la restricción a solo-ADMIN aplica específicamente al historial completo
  (US2), no a estos campos básicos.

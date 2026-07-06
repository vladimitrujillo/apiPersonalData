# Feature Specification: Restaurar Personas y Unicidad de CURP Global

**Feature Branch**: `004-restaurar-persona-curp`

**Created**: 2026-07-06

**Status**: Draft

**Input**: User description: "Permitir restaurar personas eliminadas lógicamente y ajustar las reglas de unicidad según las decisiones del proyecto. D3: unicidad de persona.correo solo entre activos (un correo de un registro eliminado puede reutilizarse). D2: CURP con unicidad global absoluta, sin excepción por estado activo/inactivo. Al crear una persona cuya CURP pertenece a un registro eliminado, 409 accionable indicando que existe un registro eliminado con esa CURP y que un ADMIN puede restaurarlo, sin exponer sus datos. Endpoint POST /personas/{id}/restaurar, solo ADMIN: activa 409, inexistente 404, conflicto de restauración únicamente por correo (indicando campo e ID del registro activo que lo ocupa, sin modificar nada), CURP nunca conflictúa al restaurar por diseño. La restauración queda en el historial como RESTAURACION. Listado de eliminados para ADMIN, mecanismo a decidir en clarify."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - El correo de una persona eliminada puede reutilizarse (Priority: P1)

Como operador que da de alta personas, necesito poder registrar a alguien nuevo con un
correo que antes perteneció a una persona ya eliminada lógicamente, para no quedar
bloqueado por un dato de contacto que ya nadie activo está usando.

**Why this priority**: Es la confirmación explícita de una regla de negocio (D3) que ya
rige hoy en el sistema para `correo`; formalizarla en este feature evita que quede
ambigua frente al cambio de CURP (US2), que sí es un cambio de comportamiento real.

**Independent Test**: Se puede verificar completamente eliminando lógicamente una
persona con un correo dado y luego dando de alta una persona nueva con ese mismo
correo, confirmando que la segunda alta procede sin error. No depende de US2, US3 ni
US4.

**Acceptance Scenarios**:

1. **Given** una persona eliminada lógicamente con correo `X`, **When** se registra una
   persona nueva con correo `X`, **Then** el alta procede sin error.
2. **Given** dos personas activas, **When** se intenta que una tome el correo de la
   otra (ambas activas), **Then** el sistema sigue rechazando con el conflicto de
   correo ya existente (comportamiento sin cambios).

---

### User Story 2 - La CURP es identidad permanente y guía hacia la restauración (Priority: P1)

Como operador que da de alta o actualiza una persona, cuando la CURP que intento usar
ya pertenece a un registro eliminado lógicamente, necesito que el sistema me lo diga de
forma clara y accionable (que existe un registro eliminado con esa CURP y que un ADMIN
puede restaurarlo), en vez de un error de duplicado genérico que no explica por qué el
alta no es posible ni qué hacer al respecto.

**Why this priority**: Es el cambio de regla central del feature (D2): la CURP pasa a
tener unicidad global absoluta, sin excepción por estado activo/inactivo — a diferencia
del correo (US1). Sin este cambio, dos registros (uno eliminado y uno nuevo) podrían
compartir CURP, lo cual contradice la identidad vitalicia de la CURP.

**Independent Test**: Se puede verificar completamente eliminando lógicamente una
persona con una CURP dada y luego intentando registrar una persona nueva con esa misma
CURP, confirmando el 409 accionable y que no revela otros datos del registro eliminado.
No depende de US1, US3 ni US4.

**Acceptance Scenarios**:

1. **Given** una persona eliminada lógicamente con CURP `Y`, **When** se intenta
   registrar una persona nueva con CURP `Y`, **Then** el sistema responde 409 con un
   mensaje que indica que existe un registro eliminado con esa CURP y que un ADMIN
   puede restaurarlo, sin incluir nombres, correo, teléfono ni otros datos personales
   del registro eliminado.
2. **Given** esa misma situación, **When** se intenta actualizar una persona existente
   asignándole la CURP `Y` (en vez de crearla), **Then** el sistema responde el mismo
   409 accionable.
3. **Given** una persona **activa** con CURP `Z`, **When** se intenta registrar o
   actualizar otra persona con CURP `Z`, **Then** el sistema responde el conflicto de
   CURP duplicado ya existente (comportamiento sin cambios para el caso activo-contra-activo).

---

### User Story 3 - Restaurar una persona eliminada lógicamente (Priority: P1)

Como ADMIN, necesito poder restaurar una persona que fue eliminada lógicamente por
error (o cuyo registro eliminado detecté a partir del 409 accionable de US2), para que
vuelva a estar activa con todos sus datos y direcciones intactos, sin tener que volver
a capturarla desde cero.

**Why this priority**: Es la capacidad que hace accionable a US2: sin poder restaurar,
el mensaje de US2 sería informativo pero no operable. Es igual de crítica que US1/US2
porque las tres reglas (correo reutilizable, CURP permanente, restauración) forman un
solo conjunto coherente de comportamiento.

**Independent Test**: Se puede verificar completamente eliminando lógicamente una
persona, restaurándola como ADMIN, y confirmando que vuelve a aparecer en consultas y
listados con sus datos y su dirección exactamente como estaban. No depende de que
exista todavía el listado de eliminados (US4) si ya se conoce el `id` a restaurar.

**Acceptance Scenarios**:

1. **Given** una persona eliminada lógicamente, **When** un ADMIN la restaura, **Then**
   vuelve a ser consultable por ID, a aparecer en el listado, y sus datos y dirección
   son exactamente los que tenía antes de eliminarse.
2. **Given** una persona que ya está activa, **When** un ADMIN intenta restaurarla,
   **Then** el sistema responde 409, sin modificar el registro.
3. **Given** un `id` que no corresponde a ninguna persona, **When** un ADMIN intenta
   restaurarla, **Then** el sistema responde 404.
4. **Given** una persona eliminada lógicamente cuyo correo ya fue tomado por otra
   persona actualmente activa, **When** un ADMIN intenta restaurarla, **Then** el
   sistema responde 409 indicando el campo (`correo`) y el `id` de la persona activa
   que lo ocupa actualmente, sin modificar ningún registro. (La CURP nunca puede
   producir este conflicto al restaurar, por construcción: al ser globalmente única,
   ningún otro registro pudo haberla tomado mientras la original estaba eliminada.)
5. **Given** un usuario con rol CAPTURISTA autenticado, **When** intenta restaurar
   cualquier persona eliminada lógicamente, **Then** el sistema responde 403.
6. **Given** una restauración exitosa, **When** se consulta el historial de esa
   persona, **Then** aparece una entrada de tipo `RESTAURACION`.

---

### User Story 4 - Ver qué personas están eliminadas (Priority: P2)

Como ADMIN, necesito poder ver cuáles personas están actualmente eliminadas
lógicamente, para poder decidir cuáles restaurar sin depender únicamente de encontrarlas
a través de un intento fallido de alta (US2).

**Why this priority**: Complementa a US3 dándole un punto de partida (sin este listado,
un ADMIN solo puede restaurar un `id` que ya conoce por otra vía). No es indispensable
para que US1-US3 funcionen y se puedan probar de forma independiente.

**Independent Test**: Con al menos una persona eliminada lógicamente, se puede verificar
completamente autenticando como ADMIN y confirmando que esa persona aparece en la vista
de eliminados, con sus datos visibles solo para ADMIN.

**Acceptance Scenarios**:

1. **Given** al menos una persona eliminada lógicamente y al menos una activa, **When**
   un ADMIN consulta la vista de personas eliminadas, **Then** solo aparecen las
   eliminadas, no las activas.
2. **Given** un usuario con rol CAPTURISTA autenticado, **When** intenta consultar la
   vista de personas eliminadas, **Then** el sistema responde 403.

---

### Edge Cases

- Restaurar y volver a eliminar la misma persona repetidamente no debe duplicar
  entradas de historial más allá de una por operación real realizada.
- Un intento de crear una persona cuya CURP pertenece a un registro eliminado, y cuyo
  correo también pertenece a ese mismo registro eliminado, responde el 409 accionable
  de CURP (US2) — el correo, al ser libremente reutilizable (US1), no es en sí mismo un
  obstáculo en este escenario.
- Restaurar una persona no reintroduce un conflicto de CURP con ningún otro registro,
  porque la unicidad global de CURP impide que haya existido nunca un duplicado
  mientras estaba eliminada.
- El 409 accionable de CURP (US2) y el 409 de conflicto de correo al restaurar (US3)
  exponen únicamente el identificador (`id`) del registro involucrado, nunca nombres,
  contacto u otros datos personales, para no filtrar información a quien no tiene
  permiso de consultar ese registro.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir que el correo de una persona eliminada
  lógicamente sea usado por una persona nueva o por la actualización de otra persona
  activa (sin cambio respecto al comportamiento actual — D3).
- **FR-002**: El sistema DEBE tratar la CURP como única de forma global y permanente:
  ninguna persona nueva ni actualización de una persona existente puede tomar una CURP
  que ya pertenezca a cualquier otro registro, esté activo o eliminado lógicamente
  (D2). Esto es un cambio respecto al comportamiento actual, donde la CURP solo era
  única entre personas activas.
- **FR-003**: Cuando la CURP en conflicto (FR-002) pertenece a un registro **activo**,
  el sistema DEBE responder con el mismo conflicto de CURP duplicado ya existente hoy
  (sin cambio de comportamiento para este caso).
- **FR-004**: Cuando la CURP en conflicto (FR-002) pertenece a un registro **eliminado
  lógicamente**, el sistema DEBE responder 409 con un mensaje accionable que indique
  que existe un registro eliminado con esa CURP y que un ADMIN puede restaurarlo,
  incluyendo el `id` de ese registro pero ningún otro dato personal suyo.
- **FR-005**: El sistema DEBE proveer una operación para restaurar una persona
  eliminada lógicamente, devolviéndola a un estado activo con sus datos y su dirección
  exactamente como estaban al momento de eliminarse.
- **FR-006**: El sistema DEBE restringir la restauración de personas únicamente a
  usuarios con rol ADMIN, respondiendo 403 a cualquier otro rol autenticado.
- **FR-007**: Un intento de restaurar una persona que ya está activa DEBE rechazarse
  con 409, sin modificar el registro.
- **FR-008**: Un intento de restaurar un `id` que no corresponde a ninguna persona DEBE
  rechazarse con 404.
- **FR-009**: Un intento de restaurar una persona cuyo correo ya pertenece a otra
  persona actualmente activa DEBE rechazarse con 409, indicando el campo (`correo`) y
  el `id` de la persona activa que lo ocupa, sin modificar ningún registro.
- **FR-010**: La restauración de una persona NUNCA DEBE rechazarse por conflicto de
  CURP, dado que la unicidad global de CURP (FR-002) hace ese conflicto imposible por
  construcción.
- **FR-011**: Cada restauración exitosa DEBE quedar registrada en el historial de la
  persona como una operación de tipo `RESTAURACION`.
- **FR-012**: El sistema DEBE proveer una vista dedicada y separada del listado
  general de personas (no un parámetro sobre `GET /api/personas`) que muestre
  únicamente las personas actualmente eliminadas lógicamente, para que el listado
  general no adquiera ramas de comportamiento distintas por rol.
- **FR-013**: El acceso a la vista de personas eliminadas (FR-012) DEBE estar
  restringido a usuarios con rol ADMIN, respondiendo 403 a cualquier otro rol.
- **FR-014**: La suite de tests automatizados existente DEBE seguir pasando en su
  totalidad tras este cambio.

### Key Entities *(include if feature involves data)*

- **Persona (regla de unicidad ampliada)**: sin cambios de atributos; cambia la regla
  de unicidad de `curp` (de "única entre activas" a "única de forma global y
  permanente", D2). La regla de unicidad de `correo` no cambia (D3, ya vigente).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los intentos de dar de alta una persona nueva con el correo de
  una persona ya eliminada lógicamente proceden sin error.
- **SC-002**: El 100% de los intentos de dar de alta o actualizar una persona con una
  CURP que ya pertenece a cualquier otro registro (activo o eliminado) son rechazados.
- **SC-003**: El 100% de esos rechazos distingue correctamente entre "pertenece a un
  registro activo" (conflicto genérico ya existente) y "pertenece a un registro
  eliminado" (mensaje accionable indicando que un ADMIN puede restaurarlo).
- **SC-004**: Es posible, de punta a punta, eliminar una persona y restaurarla
  después, terminando con exactamente los mismos datos y dirección que antes de
  eliminarla.
- **SC-005**: El 100% de los intentos de un usuario con rol CAPTURISTA de restaurar una
  persona o de consultar la vista de eliminados son rechazados.
- **SC-006**: El 100% de los intentos de restaurar una persona cuyo correo ya está en
  uso por otra persona activa son rechazados sin alterar ningún registro.
- **SC-007**: La suite de tests automatizados existente mantiene una tasa de éxito del
  100% después del cambio.

## Assumptions

- Este feature depende de que `002-autenticacion-autorizacion` (roles ADMIN/CAPTURISTA,
  autenticación) esté implementado, igual que `003-auditoria-personas` (de donde
  proviene el concepto de historial y la operación `RESTAURACION`).
- **Solapamiento con `003-auditoria-personas`**: ese feature ya especificó y planeó una
  capacidad de restauración (su US3), pero con supuestos distintos a los de este
  feature: (a) usaba el verbo `PATCH /api/personas/{id}/restaurar`, aquí se pide
  explícitamente `POST`; (b) asumía que tanto correo como CURP podían generar conflicto
  al restaurar (reutilizando los mismos chequeos "activo=true" para ambos campos),
  mientras que este feature establece que la CURP nunca puede conflictuar al restaurar,
  precisamente porque aquí se vuelve globalmente única. Este feature es la versión más
  precisa y vigente de esa capacidad; se deja constancia explícita para que, antes de
  planear o implementar cualquiera de los dos, se decida si `003` debe ceder este
  alcance a `004`, o si ambos se consolidan en una sola implementación.
- La migración necesaria para pasar la unicidad de CURP de "solo entre activas" a
  "global" es responsabilidad de la fase de planeación (`/speckit-plan`), no de esta
  especificación; aquí solo se fija el comportamiento esperado.
- El identificador (`id`) expuesto en los mensajes accionables (FR-004, FR-009) no se
  considera un dato personal por sí mismo; nombres, CURP, correo, teléfono y dirección
  del registro referido nunca se incluyen en esos mensajes.

# Feature Specification: Gestión de Personas y Catálogo de Códigos Postales

**Feature Branch**: `001-personas-codigos-postales`

**Created**: 2026-07-02

**Status**: Draft

**Input**: User description: "Construir una API para gestionar datos personales de personas... Agregar a la API una funcionalidad de consulta de códigos postales de México... Integración con el módulo de personas..."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ciclo de vida de una persona (Priority: P1)

Un cliente de la API registra una persona con sus datos personales y de contacto,
la consulta por su identificador, actualiza parcialmente sus datos y finalmente la
elimina de forma lógica, conservando el registro para fines de auditoría.

**Why this priority**: Es el núcleo del sistema — sin poder crear, leer, actualizar
y eliminar personas, ninguna otra funcionalidad (listados, catálogo de CP) tiene
sentido. Es el MVP mínimo viable.

**Independent Test**: Se puede probar de forma completamente independiente creando
una persona con datos válidos, consultándola por ID, actualizando uno de sus campos,
y eliminándola; se verifica que tras la eliminación la consulta por ID indica que
ya no está activa pero el registro no desaparece de la base de datos.

**Acceptance Scenarios**:

1. **Given** datos válidos y únicos de una persona, **When** el cliente la crea,
   **Then** el sistema responde con la persona creada y un identificador único.
2. **Given** una persona previamente creada, **When** el cliente la consulta por su
   identificador, **Then** el sistema regresa sus datos completos.
3. **Given** una persona previamente creada, **When** el cliente actualiza uno o
   varios campos (actualización parcial), **Then** el sistema conserva los campos
   no enviados sin cambios y refleja los campos modificados.
4. **Given** una persona previamente creada, **When** el cliente la elimina,
   **Then** el sistema marca el registro como eliminado (borrado lógico), sin
   removerlo físicamente de la base de datos.
5. **Given** una persona eliminada lógicamente, **When** el cliente intenta
   consultarla por ID, **Then** el sistema responde 404 indicando que no existe
   una persona activa con ese identificador.
6. **Given** un intento de crear una persona con un correo o CURP ya usado por
   otra persona activa, **When** el cliente envía la solicitud, **Then** el
   sistema responde con un error claro indicando el campo duplicado.
7. **Given** una fecha de nacimiento futura, **When** el cliente intenta crear o
   actualizar una persona con esa fecha, **Then** el sistema responde con un
   error de validación claro.
8. **Given** un correo electrónico o teléfono con formato inválido, **When** el
   cliente intenta crear o actualizar una persona, **Then** el sistema responde
   con un error de validación claro indicando el campo y la razón.

---

### User Story 2 - Listado y filtrado de personas (Priority: P2)

Un cliente de la API necesita explorar el conjunto de personas registradas,
paginando los resultados y filtrando por nombre, municipio o estado.

**Why this priority**: Depende de que existan personas creadas (US1) pero es
necesaria para cualquier caso de uso administrativo o de búsqueda; no bloquea el
MVP pero es de alto valor inmediato.

**Independent Test**: Con varias personas ya creadas (algunas eliminadas
lógicamente), se puede probar de forma independiente solicitando listados con
distintas combinaciones de filtros y páginas, y verificando que las personas
eliminadas lógicamente no aparecen.

**Acceptance Scenarios**:

1. **Given** múltiples personas activas registradas, **When** el cliente solicita
   el listado sin filtros, **Then** el sistema regresa una página de resultados
   con metadatos de paginación (total de resultados, página actual, tamaño de página).
2. **Given** personas registradas en distintos municipios y estados, **When** el
   cliente filtra por municipio o estado, **Then** el sistema regresa únicamente
   las personas activas que coinciden con el filtro.
3. **Given** personas registradas con distintos nombres, **When** el cliente
   filtra por una coincidencia parcial de nombre, **Then** el sistema regresa las
   personas activas cuyo nombre o apellidos contienen ese texto.
4. **Given** una persona eliminada lógicamente, **When** el cliente solicita
   cualquier listado (con o sin filtros), **Then** esa persona no aparece en los
   resultados.
5. **Given** una página solicitada fuera del rango de resultados existentes,
   **When** el cliente hace la solicitud, **Then** el sistema regresa una lista
   vacía sin error.

---

### User Story 3 - Consulta de catálogo de código postal exacto (Priority: P2)

Un cliente de la API consulta un código postal mexicano de 5 dígitos y obtiene el
estado, el municipio y la lista de colonias/asentamientos asociados a ese CP.

**Why this priority**: Es funcionalidad independiente de personas (puede probarse
y entregarse sola) y es prerrequisito de la integración de direcciones (US5); alto
valor para autocompletado de direcciones en general.

**Independent Test**: Con el catálogo de códigos postales previamente importado,
se puede probar de forma completamente independiente consultando un CP conocido y
verificando que la respuesta contiene estado, municipio y al menos una colonia con
su tipo de asentamiento.

**Acceptance Scenarios**:

1. **Given** un código postal existente en el catálogo, **When** el cliente lo
   consulta, **Then** el sistema regresa el estado, el municipio y la lista
   completa de colonias/asentamientos asociadas, cada una con su tipo de
   asentamiento (colonia, fraccionamiento, ejido, etc.).
2. **Given** un código postal que no existe en el catálogo, **When** el cliente lo
   consulta, **Then** el sistema responde 404 con un mensaje claro.
3. **Given** un valor que no tiene exactamente 5 dígitos numéricos, **When** el
   cliente lo envía como código postal, **Then** el sistema responde 400 con un
   mensaje claro de formato inválido.

---

### User Story 4 - Autocompletado de colonias por nombre parcial (Priority: P3)

Un cliente de la API busca colonias/asentamientos por una coincidencia parcial de
nombre, opcionalmente acotando la búsqueda a un estado o municipio, para ofrecer
autocompletado al usuario final.

**Why this priority**: Mejora la experiencia de captura de direcciones pero no es
indispensable para el ciclo básico de personas ni para la consulta exacta de CP
(US3); puede entregarse después.

**Independent Test**: Con el catálogo importado, se puede probar de forma
independiente enviando un fragmento de nombre de colonia (con y sin acotar por
estado/municipio) y verificando que los resultados contienen ese fragmento.

**Acceptance Scenarios**:

1. **Given** el catálogo de códigos postales importado, **When** el cliente busca
   colonias por un fragmento de nombre, **Then** el sistema regresa las colonias
   cuyo nombre contiene ese fragmento, incluyendo su CP, municipio y estado.
2. **Given** una búsqueda acotada a un estado o municipio específico, **When** el
   cliente la ejecuta, **Then** el sistema regresa únicamente colonias dentro de
   ese estado o municipio.
3. **Given** un fragmento de búsqueda que no coincide con ninguna colonia,
   **When** el cliente la ejecuta, **Then** el sistema regresa una lista vacía sin
   error.

---

### User Story 5 - Dirección de persona validada contra el catálogo de CP (Priority: P3)

Al registrar o actualizar la dirección de una persona, si se proporciona el código
postal, el sistema valida su existencia en el catálogo y puede autocompletar
municipio y estado, dejando que el cliente elija la colonia de la lista asociada.

**Why this priority**: Depende de US1 (personas) y US3 (catálogo de CP) ya
implementados; es la integración final que da valor agregado pero no bloquea el
uso independiente de ninguno de los dos módulos.

**Independent Test**: Con una persona existente y el catálogo de CP importado, se
puede probar de forma independiente registrando/actualizando la dirección de esa
persona con un CP válido e inválido, y verificando el comportamiento de
autocompletado y validación en cada caso.

**Acceptance Scenarios**:

1. **Given** un código postal válido y existente en el catálogo, **When** el
   cliente registra o actualiza la dirección de una persona con ese CP,
   **Then** el sistema autocompleta municipio y estado a partir del catálogo.
2. **Given** un código postal válido y existente con varias colonias asociadas,
   **When** el cliente registra la dirección indicando una colonia que pertenece a
   la lista de ese CP, **Then** el sistema acepta la dirección.
3. **Given** un código postal que no existe en el catálogo, **When** el cliente
   intenta registrar o actualizar la dirección de una persona con ese CP,
   **Then** el sistema responde con un error de validación claro y no guarda la
   dirección.
4. **Given** un código postal válido, **When** el cliente indica una colonia que
   no pertenece a la lista de colonias de ese CP, **Then** el sistema responde con
   un error de validación claro indicando las colonias válidas para ese CP.

---

### Edge Cases

- ¿Qué sucede si se intenta eliminar (borrado lógico) una persona que ya fue
  eliminada previamente? El sistema responde 404, tratándola como si no
  existiera activamente (ver Assumptions).
- ¿Qué sucede si se intenta actualizar una persona que ya fue eliminada
  lógicamente? El sistema debe responder 404, ya que una persona eliminada no
  se considera activa para modificaciones.
- ¿Qué sucede si el proceso de importación del catálogo SEPOMEX se ejecuta dos
  veces con el mismo archivo de origen? El catálogo resultante debe quedar
  idéntico, sin registros duplicados de CP ni de colonias.
- ¿Qué sucede si el proceso de importación se ejecuta con una versión más nueva
  del catálogo que modifica colonias existentes de un CP? El catálogo local debe
  reflejar la versión más reciente tras la importación.
- ¿Qué sucede si la dirección de una persona no es de México (país distinto)?
  La validación contra el catálogo de CP de SEPOMEX no aplica y el CP se acepta
  como texto libre sin validar contra el catálogo.
- ¿Qué sucede si se buscan colonias por nombre parcial sin proporcionar estado ni
  municipio? El sistema busca en todo el catálogo nacional.

## Requirements *(mandatory)*

### Functional Requirements

**Módulo de Personas**

- **FR-001**: El sistema MUST permitir crear una persona con: nombre(s),
  apellidos, fecha de nacimiento, sexo, CURP, RFC, correo electrónico, teléfono y
  dirección (calle, número, colonia, municipio, estado, código postal, país).
- **FR-002**: El sistema MUST permitir consultar una persona activa por su
  identificador único, regresando todos sus datos.
- **FR-003**: El sistema MUST permitir listar personas activas con paginación y
  filtros opcionales por nombre (coincidencia parcial), municipio y estado.
- **FR-004**: El sistema MUST permitir actualizar parcialmente los datos de una
  persona activa (solo los campos enviados se modifican).
- **FR-005**: El sistema MUST permitir eliminar una persona mediante borrado
  lógico: el registro deja de aparecer en consultas y listados pero se conserva
  en la base de datos.
- **FR-006**: El sistema MUST rechazar la creación o actualización de una persona
  cuyo correo electrónico coincida con el de otra persona activa (la unicidad no
  aplica contra personas eliminadas lógicamente; ver Assumptions).
- **FR-007**: El sistema MUST rechazar la creación o actualización de una persona
  cuyo CURP coincida con el de otra persona activa (misma regla que FR-006).
- **FR-008**: El sistema MUST rechazar una fecha de nacimiento posterior a la
  fecha actual.
- **FR-009**: El sistema MUST validar el formato del correo electrónico y
  rechazar valores que no cumplan un formato de correo válido.
- **FR-010**: El sistema MUST validar que el teléfono tenga el formato nacional
  mexicano de 10 dígitos numéricos (sin código de país) y rechazar valores que no
  lo cumplan.
- **FR-011**: El sistema MUST responder con un mensaje de error claro y
  específico (campo y motivo) ante cualquier dato inválido o duplicado.
- **FR-012**: El sistema MUST responder 404 al consultar, actualizar o eliminar
  una persona que no existe o que ya fue eliminada lógicamente.
- **FR-023**: El sistema MUST requerir autenticación mediante una clave de API de
  servicio en todas las operaciones sobre datos personales (creación, consulta,
  listado, actualización y eliminación de personas), en cumplimiento del
  principio de control de acceso de la constitución del proyecto.

**Módulo de Catálogo de Códigos Postales (SEPOMEX)**

- **FR-013**: El sistema MUST permitir consultar un código postal exacto de 5
  dígitos y regresar el estado, el municipio y la lista de colonias/asentamientos
  asociadas, cada una con su tipo de asentamiento.
- **FR-014**: El sistema MUST responder 400 cuando el valor de código postal
  enviado no sea exactamente 5 dígitos numéricos.
- **FR-015**: El sistema MUST responder 404 con un mensaje claro cuando el código
  postal consultado no exista en el catálogo.
- **FR-016**: El sistema MUST permitir buscar colonias por coincidencia parcial
  de nombre, opcionalmente acotando por estado o por municipio.
- **FR-017**: El sistema MUST importar el Catálogo Nacional de Códigos Postales
  de SEPOMEX en una tabla local mediante un proceso de carga re-ejecutable que no
  duplique registros al repetirse (idempotente), reflejando la versión más
  reciente del catálogo importado.
- **FR-018**: Las respuestas de consulta del catálogo de códigos postales MUST
  ser aptas para almacenarse en caché, dado que el catálogo cambia con poca
  frecuencia.

**Integración Personas ↔ Catálogo de Códigos Postales**

- **FR-019**: Al registrar o actualizar la dirección de una persona con un
  código postal mexicano, el sistema MUST validar que ese código postal exista
  en el catálogo antes de aceptar la dirección.
- **FR-020**: Al registrar o actualizar la dirección de una persona con un
  código postal válido, el sistema MUST autocompletar el municipio y el estado a
  partir del catálogo.
- **FR-021**: Al registrar o actualizar la dirección de una persona, el sistema
  MUST validar que la colonia proporcionada pertenezca a la lista de colonias
  asociadas a ese código postal.
- **FR-022**: Cuando la dirección de una persona corresponde a un país distinto
  de México, el sistema MUST aceptar el código postal como texto libre sin
  validarlo contra el catálogo de SEPOMEX.

### Key Entities

- **Persona**: Representa a un individuo registrado en el sistema. Atributos:
  nombre(s), apellidos, fecha de nacimiento, sexo, CURP (único entre activas),
  RFC, correo electrónico (único entre activas), teléfono, dirección, estado
  (activa/eliminada lógicamente). Se relaciona con una Dirección.
- **Dirección**: Datos de ubicación de una Persona. Atributos: calle, número,
  colonia, municipio, estado, código postal, país. Se relaciona opcionalmente
  con un Código Postal del catálogo cuando el país es México.
- **Código Postal**: Entrada del catálogo SEPOMEX. Atributos: código (5
  dígitos), estado, municipio. Se relaciona con una o varias Colonias.
- **Colonia/Asentamiento**: Asentamiento asociado a un Código Postal. Atributos:
  nombre, tipo de asentamiento (colonia, fraccionamiento, ejido, etc.).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un cliente puede completar el ciclo completo crear → consultar →
  actualizar → eliminar de una persona en una sola sesión de integración, sin
  intervención manual.
- **SC-002**: El 100% de los intentos de creación o actualización con datos
  inválidos o duplicados (correo, CURP, fecha futura, formato incorrecto)
  reciben una respuesta de error clara que identifica el campo y el motivo.
- **SC-003**: Una persona eliminada lógicamente no aparece en el 100% de los
  listados y consultas posteriores, aunque su registro sigue existiendo en la
  base de datos.
- **SC-004**: Una consulta de código postal válido regresa estado, municipio y
  colonias en un solo llamado, sin requerir llamadas adicionales.
- **SC-005**: El proceso de importación del catálogo de códigos postales puede
  ejecutarse repetidamente con el mismo archivo de origen sin generar
  duplicados ni cambiar el resultado final.
- **SC-006**: Al menos el 95% de las direcciones registradas con un código
  postal mexicano válido se autocompletan correctamente con municipio y estado
  sin que el cliente los tenga que capturar manualmente.
- **SC-007**: Los clientes reciben respuestas a búsquedas de colonias por nombre
  parcial en un tiempo adecuado para uso interactivo (autocompletado mientras el
  usuario escribe).

## Assumptions

- El identificador único de una persona es asignado por el sistema al momento
  de la creación (no es proporcionado por el cliente).
- El tamaño de página por defecto para listados es de 20 resultados, con un
  máximo configurable de 100 por página.
- La búsqueda por nombre en el listado de personas aplica a nombre(s) y
  apellidos combinados, por coincidencia parcial, sin distinguir mayúsculas o
  acentos.
- El catálogo de SEPOMEX es la única fuente de verdad para códigos postales
  mexicanos; direcciones fuera de México no se validan contra este catálogo
  (ver FR-022).
- Un intento de eliminar (borrado lógico) una persona ya eliminada se trata
  como si la persona no existiera activamente (responde 404), en línea con el
  tratamiento de actualizar/consultar una persona eliminada.
- La reimportación del catálogo SEPOMEX puede actualizar colonias existentes de
  un CP (agregar, modificar o quitar) además de agregar CPs nuevos, siempre de
  forma idempotente respecto al archivo de origen.
- **Autenticación**: se asume autenticación mediante una clave de API de
  servicio (un solo cliente de confianza), sin roles de usuario individuales,
  por ser la opción más simple suficiente para un backend interno (FR-023). Si
  en el futuro se requieren roles de usuario, esto se tratará como una
  ampliación en un feature posterior.
- **Unicidad de correo/CURP**: se asume que la unicidad aplica solo entre
  personas activas; el correo o CURP de una persona eliminada lógicamente puede
  reutilizarse al crear una nueva persona (FR-006, FR-007).
- **Formato de teléfono**: se asume el formato nacional mexicano de 10 dígitos
  numéricos, sin código de país, dado que el dominio del proyecto (personas y
  catálogo SEPOMEX) es predominantemente mexicano (FR-010).

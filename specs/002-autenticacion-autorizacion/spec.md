# Feature Specification: Autenticación y Autorización

**Feature Branch**: `002-autenticacion-autorizacion`

**Created**: 2026-07-06

**Status**: Draft

**Input**: User description: "Agregar autenticación y autorización a la API existente. Contexto: revisa el contrato en /v3/api-docs y el código existente antes de especificar; los endpoints actuales no cambian su schema, solo pasan a requerir autenticación. Alcance: usuarios del sistema con usuario (único global, credencial), contraseña, nombre, rol y estado activo/inactivo, población distinta de las personas del padrón; dos roles ADMIN y CAPTURISTA; login con JWT de expiración corta y refresh token de mayor duración; endpoints existentes protegidos salvo login, refresh y Swagger (configurable por perfil); gestión de usuarios solo ADMIN (crear, listar, desactivar, restablecer contraseña) con login reservado a perpetuidad; arranque del primer ADMIN sin credenciales hardcodeadas; reglas de códigos de estado (401/403/409) y de privacidad de contraseñas."

## Clarifications

### Session 2026-07-06

- Q: ¿Qué pasa con el mecanismo actual de autenticación por `X-API-Key` (clave compartida, sin roles) que hoy protege todos los endpoints? → A: Se reemplaza por completo por JWT; se elimina el filtro de API-Key y su configuración asociada. Ningún caller (humano o de automatización) queda exento de autenticarse como un Usuario del sistema con rol.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Acceso autenticado al API existente (Priority: P1)

Como operador del sistema, necesito iniciar sesión con mi usuario y contraseña
para obtener un token con el que todas mis llamadas al API de personas y
códigos postales sean reconocidas; si no presento un token válido, el sistema
me rechaza en lugar de dejarme pasar como hoy.

**Why this priority**: Es el requisito raíz de todo el feature: sin esto, el
API sigue siendo accesible sin control, sin importar qué roles se definan
después. Es la base sobre la que todo lo demás (autorización, gestión de
usuarios) se apoya.

**Independent Test**: Con el sistema desplegado y un usuario ya existente, se
puede verificar completamente llamando a `POST /login` con credenciales
válidas, confirmando que se recibe un token, y confirmando que una llamada a
cualquier endpoint de personas sin ese token es rechazada. No depende de que
existan roles distintos ni de la gestión de usuarios.

**Acceptance Scenarios**:

1. **Given** un usuario activo con credenciales válidas, **When** hace login,
   **Then** recibe un token de acceso y un token de refresco.
2. **Given** ninguna credencial (sin encabezado de autenticación), **When** se
   llama a cualquier endpoint existente de personas o códigos postales,
   **Then** el sistema responde 401 con el formato de error ya usado por la
   API.
3. **Given** un token de acceso inválido, corrupto o ya expirado, **When** se
   usa para llamar a un endpoint protegido, **Then** el sistema responde 401.
4. **Given** un usuario con credenciales incorrectas o un usuario que no
   existe, **When** intenta hacer login, **Then** el sistema responde 401 con
   un mensaje genérico que no revela si el usuario existe o no.

---

### User Story 2 - Autorización por rol (Priority: P1)

Como ADMIN, necesito que las acciones sensibles (eliminar personas, gestionar
usuarios) estén reservadas a mi rol, y como CAPTURISTA necesito poder seguir
haciendo mi trabajo diario (alta, consulta, listado y actualización de
personas, y consulta de códigos postales) sin fricción, pero sin poder tocar
lo que no me corresponde.

**Why this priority**: Es la razón de ser de la palabra "autorización" en el
feature. Sin esto, cualquier usuario autenticado tendría acceso total,
contradiciendo el requisito explícito de separar responsabilidades entre
ADMIN y CAPTURISTA.

**Independent Test**: Con dos usuarios ya existentes (uno ADMIN, uno
CAPTURISTA), se puede verificar completamente autenticando como cada uno y
confirmando qué acciones acepta o rechaza el sistema para cada rol, sin
depender de la historia de refresh token ni de los flujos de gestión de
usuarios en sí.

**Acceptance Scenarios**:

1. **Given** un token válido de un usuario con rol CAPTURISTA, **When** crea,
   consulta, lista o actualiza una persona, o consulta códigos postales,
   **Then** el sistema permite la operación.
2. **Given** un token válido de un usuario con rol CAPTURISTA, **When**
   intenta eliminar una persona, **Then** el sistema responde 403.
3. **Given** un token válido de un usuario con rol CAPTURISTA, **When**
   intenta crear, listar, desactivar o restablecer la contraseña de un
   usuario del sistema, **Then** el sistema responde 403.
4. **Given** un token válido de un usuario con rol ADMIN, **When** realiza
   cualquier operación sobre personas, códigos postales o usuarios del
   sistema, **Then** el sistema permite la operación.

---

### User Story 3 - Continuidad de sesión sin re-login (Priority: P2)

Como usuario ya autenticado, necesito poder seguir trabajando después de que
mi token de acceso expire, usando mi token de refresco, sin tener que volver
a escribir mi usuario y contraseña.

**Why this priority**: Mejora la experiencia de uso del día a día una vez que
la autenticación y autorización base (US1, US2) ya existen; no es
indispensable para que el sistema sea seguro, pero sí para que sea usable en
turnos de trabajo largos.

**Independent Test**: Se puede verificar de punta a punta: login, uso del
token de acceso, expiración del token de acceso, uso del token de refresco
para obtener un nuevo token de acceso, y confirmación de que las llamadas
subsecuentes siguen funcionando. Es independiente de la gestión de usuarios.

**Acceptance Scenarios**:

1. **Given** un token de acceso que ya expiró pero un token de refresco aún
   vigente, **When** el usuario solicita renovación con el token de
   refresco, **Then** recibe un nuevo token de acceso utilizable sin volver a
   enviar usuario y contraseña.
2. **Given** un usuario que fue desactivado por un ADMIN después de haber
   iniciado sesión, **When** su token de acceso aún vigente se usa antes de
   expirar, **Then** el sistema lo sigue aceptando (la revocación no es
   instantánea); **When** intenta renovar con su token de refresco después de
   ser desactivado, **Then** el sistema responde 401 y la renovación es
   rechazada.
3. **Given** un token de refresco inválido o expirado, **When** se intenta
   renovar, **Then** el sistema responde 401.

---

### User Story 4 - Gestión de usuarios del sistema (Priority: P3)

Como ADMIN, necesito poder dar de alta, listar, desactivar y restablecer la
contraseña de los usuarios que operan el sistema, y tener la garantía de que
un nombre de usuario desactivado jamás podrá reutilizarse por accidente o a
propósito.

**Why this priority**: Es necesaria para operar el sistema en el tiempo
(altas y bajas de personal), pero el sistema ya es seguro y utilizable sin
ella si existe al menos un ADMIN sembrado inicialmente (ver Assumptions). Por
eso queda después de autenticación, autorización y continuidad de sesión.

**Independent Test**: Con un ADMIN ya autenticado, se puede verificar
completamente: crear un usuario, listarlo, desactivarlo, confirmar que ya no
puede autenticarse, e intentar crear un nuevo usuario con ese mismo login
confirmando el rechazo. No depende de las demás historias más allá de tener
un ADMIN autenticado.

**Acceptance Scenarios**:

1. **Given** un ADMIN autenticado, **When** crea un usuario nuevo con un
   login no usado antes, **Then** el usuario queda creado con estado activo
   y el rol indicado.
2. **Given** un ADMIN autenticado, **When** lista los usuarios del sistema,
   **Then** obtiene el listado sin que las contraseñas (ni sus hashes)
   aparezcan en la respuesta.
3. **Given** un ADMIN autenticado y un usuario activo, **When** lo desactiva,
   **Then** ese usuario ya no puede iniciar sesión.
4. **Given** un login que pertenece a un usuario ya desactivado, **When** un
   ADMIN intenta crear un usuario nuevo con ese mismo login, **Then** el
   sistema responde 409 y no crea el usuario.
5. **Given** un ADMIN autenticado y un usuario existente, **When** restablece
   la contraseña de ese usuario, **Then** la contraseña anterior deja de
   funcionar y la nueva sigue el mismo tratamiento de privacidad que en el
   alta (nunca en claro, nunca en logs ni respuestas).

---

### Edge Cases

- Un token con firma alterada o de otro sistema debe ser rechazado igual que
  uno inválido (401).
- Un usuario que intenta actuar sobre sí mismo (p. ej. desactivarse) sigue
  las mismas reglas de rol que cualquier otra operación de gestión de
  usuarios.
- Crear un usuario con un login que pertenece a un usuario **activo** también
  se rechaza (409), no solo el caso de logins de usuarios desactivados.
- Un usuario desactivado que intenta hacer login recibe la misma respuesta
  genérica 401 que unas credenciales incorrectas, sin indicar que la cuenta
  existe o está desactivada.
- Si el primer ADMIN ya fue sembrado y el mecanismo de arranque se ejecuta de
  nuevo (por ejemplo, en un reinicio), no se debe duplicar ni sobreescribir
  el ADMIN existente.
- La documentación Swagger, cuando está cerrada en producción, responde de
  forma consistente con el resto de rutas no autenticadas (no queda
  accesible por una ruta alterna).
- Un token de refresco que ya fue usado una vez (y por tanto rotado) debe ser
  rechazado con 401 si se intenta usar de nuevo.
- Una llamada que presente el antiguo encabezado `X-API-Key` sin un token de
  acceso JWT válido se rechaza con 401 igual que cualquier llamada sin
  autenticación; la clave de API ya no es un mecanismo de autenticación
  válido (FR-006a).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir iniciar sesión con usuario y
  contraseña, devolviendo un token de acceso de vida corta y un token de
  refresco de vida más larga cuando las credenciales son válidas.
- **FR-002**: El sistema DEBE rechazar con 401 cualquier llamada a un
  endpoint protegido que no incluya un token de acceso válido y vigente,
  usando el formato de error ya existente en la API.
- **FR-003**: El sistema DEBE rechazar con 401 los intentos de login con
  credenciales incorrectas o con un usuario inexistente, sin revelar en el
  mensaje cuál de los dos casos ocurrió.
- **FR-004**: El sistema DEBE permitir renovar el token de acceso usando un
  token de refresco vigente, sin requerir usuario y contraseña de nuevo.
- **FR-005**: El sistema DEBE rechazar con 401 cualquier intento de
  renovación con un token de refresco inválido, expirado, o perteneciente a
  un usuario ya desactivado.
- **FR-006**: El sistema DEBE mantener sin cambios el schema y comportamiento
  de los endpoints existentes de personas y códigos postales; el único
  cambio observable es que ahora exigen autenticación mediante un token de
  acceso JWT en lugar de (o adicional a) cualquier mecanismo previo.
- **FR-006a**: El sistema DEBE eliminar por completo el mecanismo previo de
  autenticación por clave de API compartida (`X-API-Key`, sin identidad de
  usuario ni rol); ninguna ruta protegida DEBE aceptar esa clave como
  sustituto válido de un token de acceso JWT.
- **FR-007**: El sistema DEBE permitir que un usuario con rol CAPTURISTA
  cree, consulte, liste y actualice personas, y consulte códigos postales.
- **FR-008**: El sistema DEBE rechazar con 403 cualquier intento de un
  usuario con rol CAPTURISTA de eliminar una persona o de acceder a
  cualquier operación de gestión de usuarios (crear, listar, desactivar,
  restablecer contraseña).
- **FR-009**: El sistema DEBE permitir que un usuario con rol ADMIN realice
  todas las operaciones existentes sobre personas y códigos postales, además
  de todas las operaciones de gestión de usuarios.
- **FR-010**: El sistema DEBE permitir que solo un usuario con rol ADMIN
  cree, liste, desactive y restablezca la contraseña de otros usuarios del
  sistema.
- **FR-011**: El sistema DEBE tratar el login de un usuario del sistema como
  una credencial de identidad: único de forma global, y jamás liberado ni
  reutilizable para otro usuario aunque el original haya sido desactivado.
- **FR-012**: El sistema DEBE rechazar con 409 cualquier intento de crear un
  usuario cuyo login ya pertenezca a otro usuario, esté este activo o
  desactivado.
- **FR-013**: El sistema DEBE impedir que un usuario desactivado inicie
  sesión, respondiendo igual que ante credenciales incorrectas (401
  genérico).
- **FR-014**: Un token de acceso emitido antes de que su usuario fuera
  desactivado sigue siendo válido hasta su expiración natural; el sistema
  DEBE, sin embargo, rechazar cualquier intento de refresh de ese usuario en
  cuanto está desactivado.
- **FR-015**: El sistema DEBE mantener a los usuarios del sistema (login,
  contraseña, nombre, rol, estado) como una población separada de las
  personas registradas en el padrón: no comparten tabla ni el campo de
  correo, y las credenciales de un usuario del sistema nunca se derivan de
  datos de contacto de una persona.
- **FR-016**: El sistema DEBE almacenar las contraseñas únicamente en forma
  de hash (nunca en texto plano) y DEBE excluirlas —en cualquier
  representación, incluido el hash— de logs y de toda respuesta de la API.
- **FR-017**: El sistema DEBE proveer un mecanismo de arranque que garantice
  la existencia de al menos un usuario ADMIN inicial, a partir de
  configuración provista por quien opera el despliegue, sin que ninguna
  credencial quede escrita de forma fija en el código fuente; ejecutar este
  mecanismo más de una vez no debe duplicar ni sobrescribir el ADMIN ya
  existente.
- **FR-018**: El sistema DEBE permitir que la documentación Swagger quede
  abierta o cerrada según el perfil de despliegue (abierta en desarrollo,
  cerrada en producción), sin exigir autenticación para login, refresh, ni
  para la documentación cuando esté configurada como abierta.
- **FR-019**: El sistema DEBE responder con 403, usando el formato de error
  existente, cuando un usuario autenticado con un rol válido intenta una
  operación para la que su rol no está autorizado.
- **FR-020**: La suite de tests automatizados existente DEBE seguir pasando
  en su totalidad tras este cambio, ajustada donde sea necesario para
  autenticarse antes de ejercitar los endpoints ya cubiertos.
- **FR-021**: Cuando un ADMIN restablece la contraseña de un usuario, el ADMIN
  DEBE proveer la nueva contraseña directamente en la solicitud; el sistema
  DEBE invalidar la contraseña anterior de forma inmediata al aplicar el
  restablecimiento.
- **FR-022**: Cada vez que se usa un token de refresco para renovar el token
  de acceso, el sistema DEBE emitir un nuevo token de refresco e invalidar el
  anterior (rotación en cada uso), de modo que un token de refresco ya usado
  no pueda reutilizarse.

### Key Entities *(include if feature involves data)*

- **Usuario del sistema**: Persona operadora del sistema (no del padrón),
  identificada por un login único de forma global y permanente. Atributos:
  login, contraseña (solo como hash), nombre, rol, estado activo/inactivo.
  Su login, una vez usado, queda reservado para siempre aunque se desactive.
- **Rol**: ADMIN (acceso total, incluida gestión de usuarios) o CAPTURISTA
  (alta, consulta, listado y actualización de personas; consulta de
  códigos postales). Cada usuario del sistema tiene exactamente un rol.
- **Token de acceso**: Credencial de corta duración que un usuario
  autenticado presenta en cada llamada al API para probar su identidad y
  rol vigentes.
- **Token de refresco**: Credencial de mayor duración que permite obtener un
  nuevo token de acceso sin volver a autenticarse con usuario y contraseña;
  deja de ser utilizable si el usuario asociado se desactiva.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las llamadas sin token a los endpoints existentes de
  personas y códigos postales son rechazadas con 401.
- **SC-002**: Un usuario con rol CAPTURISTA completa el 100% de sus tareas
  diarias permitidas (alta, consulta, listado, actualización de personas,
  consulta de códigos postales) sin necesitar intervención de un ADMIN, y el
  100% de sus intentos de eliminar personas o gestionar usuarios son
  rechazados.
- **SC-003**: Un usuario puede mantener una sesión de trabajo activa más
  allá de la vida del token de acceso, usando el token de refresco, sin
  volver a escribir usuario y contraseña, de forma verificable de punta a
  punta (login → uso → expiración → refresh → uso).
- **SC-004**: El 100% de los intentos de crear un usuario con un login ya
  usado (activo o desactivado) son rechazados con 409, garantizando que
  ningún login se reutiliza jamás.
- **SC-005**: La suite de tests automatizados existente mantiene una tasa de
  éxito del 100% después del cambio.

## Assumptions

- La vida útil exacta del token de acceso y del token de refresco no fue
  especificada; se asume un valor corto estándar para el de acceso (del
  orden de minutos) y uno considerablemente mayor para el de refresco (del
  orden de días), ajustable por configuración.
- No se requiere una operación explícita de "logout"/revocación inmediata de
  tokens de acceso vigentes; la mitigación ante una cuenta comprometida o
  desactivada es la vida corta del token de acceso combinada con el bloqueo
  del refresh (FR-014).
- No se especificaron reglas de complejidad de contraseña más allá de "hash
  fuerte, nunca en claro"; se asume una política mínima estándar (longitud
  mínima razonable), sin reglas adicionales de composición.
- No se especificó límite de intentos fallidos de login (protección contra
  fuerza bruta); queda fuera del alcance de este feature.
- "Consultar códigos postales" incluye tanto la consulta de códigos postales
  como la búsqueda de colonias asociadas, ya que ambas son parte del mismo
  catálogo de referencia de solo lectura.
- El mecanismo de arranque del primer ADMIN (semilla por configuración o
  migración) es una decisión técnica que se resuelve en la fase de
  planeación; esta especificación solo exige que exista, sea seguro (sin
  credenciales fijas en el código) y sea idempotente.
- Un usuario del sistema no puede tener más de un rol simultáneamente.

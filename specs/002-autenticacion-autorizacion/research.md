# Research: Autenticación y Autorización

El stack base fue especificado explícitamente por el solicitante (Spring Security 6,
JWT firmado con llave por configuración, BCrypt, tablas nuevas `usuario`/`refresh_token`
vía Flyway, `@PreAuthorize`, springdoc con esquema bearer). No quedan marcadores
`NEEDS CLARIFICATION` en el Technical Context. Este documento resuelve las decisiones de
diseño derivadas necesarias para aplicar ese stack de forma consistente con el spec y la
constitución del proyecto.

## 1. Algoritmo de firma del JWT: HS256 vs RS256

**Decision**: HS256 (HMAC-SHA256) con una llave simétrica de al menos 256 bits, provista
exclusivamente por configuración externa (`JWT_SECRET`), sin valor por defecto en
`application.yml` (a diferencia de `API_KEY`, que sí tenía un default de desarrollo). El
arranque falla si `JWT_SECRET` no está definida.

**Rationale**: Esta aplicación es un monolito único que firma y valida sus propios
tokens; nada más necesita verificarlos de forma independiente. RS256 (par de llaves
asimétrico) solo aporta valor cuando un verificador no debe tener la capacidad de firmar
(p. ej. múltiples servicios independientes verificando tokens emitidos por un IdP
central) — no es el caso aquí, así que introducir gestión de llaves asimétricas sería
complejidad no justificada (Principio IV). No dar un default de desarrollo para
`JWT_SECRET` es una divergencia deliberada del patrón de `API_KEY`: comprometer esta
llave permite forjar tokens de cualquier rol (incluido ADMIN), un impacto mayor que el
de la clave de API que reemplaza.

**Alternatives considered**:
- RS256 desde el inicio: rechazado por ahora (YAGNI); si en el futuro otro servicio
  necesita validar tokens sin la capacidad de firmarlos, es una migración aislada al
  `JwtService` sin impacto en el resto del diseño.
- Reusar el patrón de `API_KEY` con un default `changeme-*` para desarrollo local:
  rechazado por la sensibilidad mayor de esta llave (ver Rationale).

## 2. Librería de JWT

**Decision**: `io.jsonwebtoken:jjwt-api`/`jjwt-impl`/`jjwt-jackson` (JJWT) para firmar y
parsear el JWT de acceso, encapsulado en un único `JwtService` (`common.security`). No se
adopta el módulo `spring-security-oauth2-resource-server`/Nimbus.

**Rationale**: Ese módulo de Spring Security está diseñado para *validar* tokens emitidos
por un Authorization Server externo (issuer-uri, JWK sets, etc.); aquí la propia
aplicación emite y valida sus tokens con una llave simétrica local, un caso mucho más
simple que JJWT cubre directamente con una API mínima (Principio IV).

**Alternatives considered**:
- `spring-security-oauth2-resource-server` + `NimbusJwtDecoder`: rechazado por modelar un
  escenario (IdP externo) que no aplica aquí; añadiría configuración innecesaria
  (issuer, JWK endpoint) para un emisor/validador que son el mismo proceso.

## 3. Representación del token de refresco: JWT vs opaco con estado en BD

**Decision**: El token de refresco es una cadena aleatoria opaca de alta entropía (no un
JWT), generada con un generador criptográficamente seguro. Solo su **hash** (SHA-256) se
persiste en la tabla `refresh_token`; el valor en claro se entrega una sola vez al
cliente y no se puede reconstruir a partir de lo almacenado. Cada fila referencia a
`usuario` (FK), tiene `expira_en` y `revocado` (boolean). Al usarse exitosamente para
renovar (decisión D2, ya tomada — rotación en cada uso), la fila usada se marca
`revocado = true` y se inserta una fila nueva para el token de refresco emitido.

**Rationale**: La rotación y la invalidación inmediata al desactivar un usuario (spec
FR-005/FR-014) requieren de todos modos una fila por token en BD; hacer el token también
un JWT autocontenido sería una segunda vía de firma/parseo redundante para un valor cuyo
estado real vive en la base de datos (Principio IV). Guardar solo el hash (no el valor
en claro) sigue el mismo principio que ya aplica a `usuario.password_hash`: un volcado de
la tabla no debe permitir suplantar sesiones activas.

**Alternatives considered**:
- Token de refresco también como JWT, con `jti` registrado en `refresh_token` para poder
  revocarlo: rechazado por redundante — dos mecanismos de firma para un solo problema que
  el registro en BD ya resuelve por completo.
- Guardar el valor del token de refresco en claro en BD: rechazado; un acceso de lectura
  a la base de datos no debe ser equivalente a poder reutilizar sesiones activas.

## 4. Autorización por rol: dónde aplicar `@PreAuthorize`

**Decision**: `@EnableMethodSecurity` + `@PreAuthorize("hasRole('ADMIN')")` /
`hasAnyRole('ADMIN','CAPTURISTA')` a nivel de método de **controller**, junto a las
anotaciones OpenAPI (`@Operation`) ya existentes en esos mismos métodos. El rol de
`Usuario` se mapea a la autoridad `ROLE_<rol>` en la capa de autenticación
(`JwtAuthenticationFilter`/`UserDetailsService`).

**Rationale**: Mantiene la regla de autorización visible junto a la documentación del
endpoint (mismo lugar donde ya se lee "qué hace este endpoint"), en vez de enterrarla en
el `service`. Es la ubicación estándar de Spring Security y la más simple suficiente para
dos roles (Principio IV).

**Alternatives considered**:
- `@PreAuthorize` en el `service`: rechazado — separaría la regla de autorización de la
  definición pública del endpoint, dificultando auditar qué rol requiere cada ruta.
- Autorización manual (`if` explícito comprobando el rol del principal) en cada método:
  rechazada por ser más verbosa y propensa a omisiones que la anotación declarativa.

## 5. Rutas públicas y Swagger condicionado por perfil

**Decision**: Nueva propiedad `app.security.swagger-publico` (boolean, default `true`),
leída por `SecurityConfig` para decidir si `/v3/api-docs/**`, `/swagger-ui/**` y
`/swagger-ui.html` se agregan a la lista de rutas sin autenticación. `POST /login` y
`POST /refresh` son siempre públicas. Un perfil `prod` (`application-prod.yml` o
variable de entorno) sobreescribe la propiedad a `false`.

**Rationale**: Reutiliza el mecanismo estándar de propiedades/perfiles de Spring Boot ya
usado en el proyecto (`application.yml` con placeholders `${VAR:default}`) en vez de
introducir un mecanismo de configuración nuevo (Principio I/IV).

**Alternatives considered**:
- Condicionar con `@Profile` sobre un `@Bean` de reglas de seguridad distinto por
  entorno: rechazado por duplicar casi toda la cadena de filtros solo para variar una
  lista de rutas; una propiedad booleana es más simple.

## 6. Bootstrap del primer ADMIN: migración vs `ApplicationRunner`

**Decision**: `ApplicationRunner` (`AdminBootstrapRunner`, en `usuario.bootstrap`,
análogo a `codigopostal.importer`) ejecutado después de que Flyway aplica las
migraciones. En cada arranque: si ya existe algún `usuario` con `rol = ADMIN`, no hace
nada (idempotente). Si no existe ninguno, lee `ADMIN_BOOTSTRAP_LOGIN` /
`ADMIN_BOOTSTRAP_PASSWORD` de variables de entorno; si ambas están presentes, crea el
ADMIN con la contraseña hasheada con BCrypt; si faltan, registra un `WARN` (sin
contraseñas en el mensaje) y continúa el arranque sin bloquear la aplicación.

**Rationale**: Una migración Flyway SQL no tiene acceso al `PasswordEncoder` de Spring
para hashear la contraseña sin recurrir a una migración Java (`JavaMigration`), que
rompería el estilo 100% SQL de las migraciones existentes (Principio I). Hacer el
seed en código de aplicación mantiene las migraciones puramente de esquema (Principio V)
y el hashing donde ya vive el resto de la lógica de contraseñas (Principio IV).

**Alternatives considered**:
- Migración Flyway `JavaMigration` que hashea e inserta el ADMIN: rechazada por romper el
  estilo homogéneo de migraciones SQL puras ya establecido (`V1__create_schema.sql`).
- Bloquear el arranque de la aplicación si no hay ADMIN y no se proveen las variables:
  rechazado — impediría arrancar el sistema en escenarios legítimos (p. ej. pruebas
  unitarias que no ejercitan autenticación), y el propio spec no exige que el arranque
  falle, solo que exista un mecanismo seguro de siembra.

## 7. Adaptación de la suite de tests existente (retiro de `X-API-Key`)

**Decision**:
- Tests `@WebMvcTest` (rebanada web, sin arrancar todo el contexto de seguridad real):
  se agrega la dependencia `spring-security-test` y se usa `@WithMockUser(roles = "...")`
  para simular el principal autenticado, reemplazando
  `@TestPropertySource(properties = "app.security.api-key=...")`.
- Tests de integración (`*IT.java`, `@SpringBootTest` con Testcontainers): se agrega un
  helper `TestJwt` (mismo rol que `TestApiKey` hoy) que hace login real contra
  `POST /login` con un usuario ADMIN de prueba (sembrado por el propio test o disponible
  vía el bootstrap) y añade `Authorization: Bearer <token>` a cada request.

**Rationale**: Sigue el patrón ya existente de una clase de utilidad de test compartida
(`TestApiKey` → `TestJwt`), y usa el mecanismo idiomático de Spring Security para cada
tipo de test (mock de principal en rebanadas web, flujo real end-to-end en IT), cumpliendo
FR-020 sin inventar un tercer mecanismo de autenticación de prueba.

**Alternatives considered**:
- Generar tokens JWT "a mano" en los tests reutilizando `JwtService` sin pasar por
  `@WithMockUser`: rechazado para los `@WebMvcTest` por ser más verboso que la anotación
  estándar de `spring-security-test` sin ganar fidelidad adicional (esas pruebas no
  arrancan el filtro real de todas formas).

## 8. Representación de `Rol` en base de datos

**Decision**: Columna `rol VARCHAR(20) NOT NULL` con `CHECK (rol IN ('ADMIN',
'CAPTURISTA'))`, igual que el estilo ya usado por `persona.sexo` (`VARCHAR(20)`, sin tipo
`ENUM` nativo de PostgreSQL). En Java, un enum `Rol { ADMIN, CAPTURISTA }` mapeado con
`@Enumerated(EnumType.STRING)`.

**Rationale**: Consistencia directa con la única precedencia de "campo con dominio
cerrado de valores" ya presente en el esquema (Principio I), evitando introducir un tipo
`ENUM` nativo de Postgres que no se usa en ningún otro lado del esquema.

**Alternatives considered**:
- Tipo `ENUM` nativo de PostgreSQL: rechazado por no tener precedente en el esquema
  existente y por la fricción adicional de Flyway al añadir valores nuevos en el futuro.

## 9. Unicidad e inmutabilidad del login de `usuario`

**Decision**: `UNIQUE` simple (no parcial) sobre `usuario.login`, sin excepción por
estado `activo`. Combinado con la regla de negocio (aplicada en `UsuarioService`, nunca
en el controller) de que un usuario **nunca se borra físicamente**, solo se desactiva
(`activo = false`), esta única restricción `UNIQUE` global es suficiente para garantizar
que un login jamás se reutiliza (FR-011, FR-012, Constitución Principio VI).

**Rationale**: A diferencia de `persona.correo`/`persona.curp` (únicos solo entre
activos, vía índice parcial — decisión ya tomada en `specs/001.../research.md` §2), aquí
la regla de negocio es la opuesta: unicidad global permanente. Un `UNIQUE` simple más la
prohibición absoluta de `DELETE` sobre la tabla `usuario` (solo `UPDATE ... SET activo =
false`) modela exactamente esa semántica sin lógica adicional.

**Alternatives considered**:
- Índice único parcial `WHERE activo = true` (igual que `persona`): rechazado
  explícitamente — es la semántica contraria a la requerida por D1/Principio VI de la
  constitución (el login de `usuario` es credencial, no dato de contacto).

## 10. Nueva migración Flyway

**Decision**: `V2__create_usuario_refresh_token.sql`, puramente aditiva:
`CREATE TABLE usuario (...)`, `CREATE TABLE refresh_token (...)`, sin datos semilla (el
ADMIN inicial se crea en código — decisión #6). `V1__create_schema.sql` no se modifica.

**Rationale**: Cumple el Principio V (migraciones solo aditivas y versionadas) de forma
directa; es la primera migración añadida desde `V1`, consistente con el flujo esperado.

## Resumen de resolución de NEEDS CLARIFICATION

No quedaban marcadores `NEEDS CLARIFICATION` pendientes en el Technical Context: el
stack fue provisto explícitamente por el solicitante. Las secciones anteriores
documentan las decisiones de diseño derivadas necesarias para implementarlo cumpliendo
el spec y la constitución del proyecto.

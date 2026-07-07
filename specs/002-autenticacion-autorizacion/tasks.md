---

description: "Task list for Autenticación y Autorización"
---

# Tasks: Autenticación y Autorización

**Input**: Design documents from `/specs/002-autenticacion-autorizacion/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED. El Principio III de la constitución (`.specify/memory/constitution.md`)
es Test-First con Suite Siempre Verde y NON-NEGOTIABLE — todas las historias de usuario
incluyen tareas de test que deben escribirse y fallar antes de la implementación
correspondiente, y la suite existente debe seguir en verde al final (FR-020, SC-005).

**Organization**: Tareas agrupadas por historia de usuario (spec.md) para permitir
implementación y prueba independiente de cada una.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: Historia de usuario a la que pertenece (US1..US4)
- Cada tarea incluye la ruta de archivo exacta

## Path Conventions

Proyecto único Maven/Spring Boot (ver `plan.md` → Project Structure):

- `src/main/java/mx/personas/api/...`
- `src/main/resources/db/migration/...`
- `src/test/java/mx/personas/api/...`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Dependencias y configuración base para JWT/Spring Security

- [X] T001 Agregar dependencias a `pom.xml`: `spring-boot-starter-security`,
      `io.jsonwebtoken:jjwt-api`, `io.jsonwebtoken:jjwt-impl` (runtime),
      `io.jsonwebtoken:jjwt-jackson` (runtime), y `spring-security-test` (scope `test`)
      per research.md §1-2
- [X] T002 [P] Actualizar `src/main/resources/application.yml`: quitar
      `app.security.api-key` (FR-006a); agregar `app.security.jwt-secret: ${JWT_SECRET}`
      (sin default — research.md §1), `app.security.access-token-ttl-minutes`,
      `app.security.refresh-token-ttl-days`, `app.security.swagger-publico:
      ${SWAGGER_PUBLICO:true}` (research.md §5), y
      `app.security.admin-bootstrap-login: ${ADMIN_BOOTSTRAP_LOGIN:}` /
      `app.security.admin-bootstrap-password: ${ADMIN_BOOTSTRAP_PASSWORD:}` (research.md §6)
- [X] T003 [P] Espejar los mismos cambios de propiedades en
      `src/test/resources/application.yml`, fijando un `JWT_SECRET` de prueba y
      `app.security.swagger-publico: true`

**Checkpoint**: Compila con las dependencias nuevas; configuración lista para Fase 2

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Infraestructura de autenticación/autorización que TODAS las historias
necesitan (Spring Security, JWT, entidades `usuario`/`refresh_token`, bootstrap del
primer ADMIN). Ninguna historia de usuario puede probarse sin esto.

**⚠️ CRITICAL**: No se puede iniciar ninguna historia de usuario hasta completar esta fase

- [X] T004 Migración `src/main/resources/db/migration/V2__create_usuario_refresh_token.sql`:
      `CREATE TABLE usuario` (id UUID PK, login TEXT UNIQUE NOT NULL, password_hash TEXT
      NOT NULL, nombre TEXT NOT NULL, rol VARCHAR(20) NOT NULL CHECK IN ('ADMIN',
      'CAPTURISTA'), activo BOOLEAN NOT NULL DEFAULT true, created_at/updated_at) y
      `CREATE TABLE refresh_token` (id UUID PK, usuario_id UUID FK → usuario, token_hash
      TEXT NOT NULL, expira_en TIMESTAMPTZ NOT NULL, revocado BOOLEAN NOT NULL DEFAULT
      false, created_at), sin datos semilla, per data-model.md y research.md §9-10
- [X] T005 [P] Enum `Rol` en `src/main/java/mx/personas/api/usuario/model/Rol.java`
      (`ADMIN`, `CAPTURISTA`) per research.md §8
- [X] T006 [P] Entidad `Usuario` en
      `src/main/java/mx/personas/api/usuario/model/Usuario.java` (JPA, `@Enumerated
      (EnumType.STRING)` para `rol`, campos per data-model.md § usuario)
- [X] T007 [P] Entidad `RefreshToken` en
      `src/main/java/mx/personas/api/usuario/model/RefreshToken.java` (JPA, FK a
      `Usuario`, campos per data-model.md § refresh_token)
- [X] T008 [P] `UsuarioRepository` en
      `src/main/java/mx/personas/api/usuario/repository/UsuarioRepository.java`
      (`findByLogin`, extiende `JpaRepository<Usuario, UUID>`)
- [X] T009 [P] `RefreshTokenRepository` en
      `src/main/java/mx/personas/api/usuario/repository/RefreshTokenRepository.java`
      (`findByTokenHash`, extiende `JpaRepository<RefreshToken, UUID>`)
- [X] T010 Agregar a `src/main/java/mx/personas/api/common/error/ErrorCode.java`:
      `ACCESO_DENEGADO(HttpStatus.FORBIDDEN)`,
      `USUARIO_LOGIN_DUPLICADO(HttpStatus.CONFLICT)`,
      `USUARIO_NO_ENCONTRADO(HttpStatus.NOT_FOUND)` (contracts/auth-api.md,
      contracts/usuarios-api.md)
- [X] T011 `JwtService` en
      `src/main/java/mx/personas/api/common/security/JwtService.java`: firmar (HS256,
      claims `sub`=login y `rol`), parsear y validar tokens de acceso usando JJWT y la
      llave de `app.security.jwt-secret`; expone también generación de un valor opaco de
      alta entropía y su hash SHA-256 para el token de refresco (research.md §1-3)
- [X] T012 `SecurityConfig` en
      `src/main/java/mx/personas/api/common/security/SecurityConfig.java`:
      `SecurityFilterChain` sin estado (`SessionCreationPolicy.STATELESS`), `@Bean
      PasswordEncoder` (`BCryptPasswordEncoder`), `@EnableMethodSecurity`, rutas públicas
      `POST /login`, `POST /refresh`, y `/v3/api-docs/**`+`/swagger-ui/**`+
      `/swagger-ui.html` condicionadas a `app.security.swagger-publico` (research.md §5),
      registra `JwtAuthenticationFilter` antes del filtro estándar de usuario/contraseña
- [X] T013 `JwtAuthenticationFilter` en
      `src/main/java/mx/personas/api/common/security/JwtAuthenticationFilter.java`
      (`OncePerRequestFilter`, mismo patrón que `ApiKeyAuthFilter`): lee
      `Authorization: Bearer <token>`, valida con `JwtService`, si falta/es inválido/está
      expirado escribe `401 NO_AUTENTICADO` en el formato `ApiError` existente y no
      continúa la cadena; si es válido, coloca un `Authentication` con autoridad
      `ROLE_<rol>` en el `SecurityContext` (FR-002)
- [X] T014 Eliminar `src/main/java/mx/personas/api/common/security/ApiKeyAuthFilter.java`
      (FR-006a — el mecanismo de `X-API-Key` se retira por completo)
- [X] T015 [P] Crear `src/test/java/mx/personas/api/common/TestJwt.java` reemplazando a
      `TestApiKey`: helper para pruebas `@WebMvcTest` (constantes de rol/autoridad para
      `@WithMockUser`) y para pruebas de integración (login real contra `POST /login` y
      construcción del header `Authorization: Bearer <token>`) per research.md §7
- [X] T016 [P] Modificar
      `src/main/java/mx/personas/api/common/config/OpenApiConfig.java`: reemplazar el
      esquema `ApiKeyAuth` por un esquema `bearer` (`SecurityScheme.Type.HTTP`,
      `scheme("bearer")`, `bearerFormat("JWT")`)
- [X] T017 `AdminBootstrapRunner` en
      `src/main/java/mx/personas/api/usuario/bootstrap/AdminBootstrapRunner.java`
      (`ApplicationRunner`, análogo a `SepomexImportRunner`): si ya existe un `usuario`
      con `rol = ADMIN` no hace nada; si no, lee `app.security.admin-bootstrap-login` /
      `-password`, y si ambas están presentes crea el ADMIN con contraseña hasheada
      (BCrypt); si faltan, registra un `WARN` sin credenciales y continúa el arranque
      (FR-017, research.md §6)

**Checkpoint**: La aplicación arranca, siembra el ADMIN de forma idempotente, y protege
todas las rutas (salvo login/refresh/swagger) exigiendo un JWT válido. Ninguna historia de
usuario es aún accesible (no existe `POST /login`) — eso es US1.

---

## Phase 3: User Story 1 - Acceso autenticado al API existente (Priority: P1) 🎯 MVP

**Goal**: Login con usuario/contraseña emite un token de acceso; todos los endpoints
existentes de personas y códigos postales exigen ese token (401 si falta o es inválido).

**Independent Test**: Con el ADMIN sembrado por el bootstrap, `POST /login` con
credenciales válidas devuelve un token; llamar a un endpoint de personas sin token
responde 401; con el token, responde normalmente.

### Tests for User Story 1 ⚠️

> **NOTE: Escribir estas pruebas PRIMERO, confirmar que fallan antes de implementar**

- [X] T018 [P] [US1] `@WebMvcTest` `AuthControllerTest` en
      `src/test/java/mx/personas/api/auth/AuthControllerTest.java`: login con
      credenciales válidas → 200 con `accessToken`+`refreshToken`+`expiraEn`; login con
      credenciales incorrectas → 401 `NO_AUTENTICADO`; login con usuario inexistente →
      mismo 401 genérico; login con usuario desactivado → mismo 401 genérico (FR-001,
      FR-003, FR-013, Acceptance Scenarios US1 #1 y #4)
- [X] T019 [P] [US1] Test unitario `AuthServiceTest` en
      `src/test/java/mx/personas/api/auth/AuthServiceTest.java` (Mockito sobre
      `UsuarioRepository`/`PasswordEncoder`/`JwtService`): mismas reglas de login que
      T018 a nivel de servicio, sin distinguir en el mensaje/lógica si el usuario no
      existe vs. contraseña incorrecta vs. desactivado
- [X] T020 [P] [US1] Integration test `AuthIT` en
      `src/test/java/mx/personas/api/integration/AuthIT.java` (extiende
      `AbstractIntegrationTest`): llamar `/api/personas` sin header → 401; login real con
      el ADMIN de prueba → 200; llamar `/api/personas` con el `accessToken` obtenido →
      200; llamar con un token corrupto/con firma alterada → 401 (Acceptance Scenarios
      US1 #2 y #3, Edge Case "token con firma alterada")

### Adaptar la suite existente para autenticarse (FR-020, research.md §7)

- [X] T021 [P] [US1] Adaptar
      `src/test/java/mx/personas/api/persona/PersonaControllerCreateTest.java`: usar
      `@WithMockUser` (rol ADMIN) vía `TestJwt` en vez del header `X-API-Key`
- [X] T022 [P] [US1] Adaptar
      `src/test/java/mx/personas/api/persona/PersonaControllerDeleteTest.java`: usar
      `@WithMockUser` (rol ADMIN) vía `TestJwt` en vez del header `X-API-Key`
- [X] T023 [P] [US1] Adaptar
      `src/test/java/mx/personas/api/persona/PersonaControllerGetTest.java`: usar
      `@WithMockUser` (rol ADMIN) vía `TestJwt` en vez del header `X-API-Key`
- [X] T024 [P] [US1] Adaptar
      `src/test/java/mx/personas/api/persona/PersonaControllerListTest.java`: usar
      `@WithMockUser` (rol ADMIN) vía `TestJwt` en vez del header `X-API-Key`
- [X] T025 [P] [US1] Adaptar
      `src/test/java/mx/personas/api/persona/PersonaControllerUpdateTest.java`: usar
      `@WithMockUser` (rol ADMIN) vía `TestJwt` en vez del header `X-API-Key`
- [X] T026 [P] [US1] Adaptar
      `src/test/java/mx/personas/api/codigopostal/CodigoPostalControllerTest.java`: usar
      `@WithMockUser` (rol ADMIN o CAPTURISTA) vía `TestJwt` en vez del header
      `X-API-Key`
- [X] T027 [P] [US1] Adaptar
      `src/test/java/mx/personas/api/codigopostal/ColoniaControllerTest.java`: usar
      `@WithMockUser` (rol ADMIN o CAPTURISTA) vía `TestJwt` en vez del header
      `X-API-Key`
- [X] T028 [P] [US1] Adaptar
      `src/test/java/mx/personas/api/integration/CodigoPostalIT.java`: reemplazar el
      header `X-API-Key` por login real + `Authorization: Bearer` vía `TestJwt`
- [X] T029 [P] [US1] Adaptar
      `src/test/java/mx/personas/api/integration/PersonaDireccionIT.java`: reemplazar el
      header `X-API-Key` por login real + `Authorization: Bearer` vía `TestJwt`
- [X] T030 [P] [US1] Adaptar
      `src/test/java/mx/personas/api/integration/PersonaDireccionValidationIT.java`:
      reemplazar el header `X-API-Key` por login real + `Authorization: Bearer` vía
      `TestJwt`
- [X] T031 [P] [US1] Adaptar
      `src/test/java/mx/personas/api/integration/PersonaLifecycleIT.java`: reemplazar el
      header `X-API-Key` por login real + `Authorization: Bearer` vía `TestJwt`
- [X] T032 [P] [US1] Adaptar
      `src/test/java/mx/personas/api/integration/PersonaListIT.java`: reemplazar el
      header `X-API-Key` por login real + `Authorization: Bearer` vía `TestJwt`
- [X] T033 [P] [US1] Adaptar `src/test/java/mx/personas/api/common/PiiLoggingIT.java`:
      reemplazar el header `X-API-Key` por login real + `Authorization: Bearer` vía
      `TestJwt`

### Implementation for User Story 1

- [X] T034 [US1] DTOs `LoginRequestDTO` y `TokenResponseDTO` en
      `src/main/java/mx/personas/api/auth/dto/LoginRequestDTO.java` y
      `TokenResponseDTO.java` (`accessToken`, `refreshToken`, `expiraEn` — contracts/auth-api.md)
- [X] T035 [US1] `AuthService.login(...)` en
      `src/main/java/mx/personas/api/auth/service/AuthService.java`: busca por `login`,
      valida contraseña con `PasswordEncoder`, valida `activo`, si todo correcto emite
      access token (`JwtService`) y crea/persiste un `refresh_token` nuevo; en cualquier
      caso de fallo (no existe, contraseña incorrecta, desactivado) lanza la misma
      excepción `401 NO_AUTENTICADO` sin distinguir el motivo (FR-001, FR-003, FR-013)
- [X] T036 [US1] `AuthController` en
      `src/main/java/mx/personas/api/auth/controller/AuthController.java`: `POST /login`
      (depende de T034, T035)
- [X] T037 [US1] Eliminar `src/test/java/mx/personas/api/common/TestApiKey.java` una vez
      que T021-T033 ya no lo referencian (depende de T021-T033)

**Checkpoint**: US1 funcional y probable de forma independiente — login emite tokens,
todo endpoint existente exige `Authorization: Bearer`, suite completa en verde con el
nuevo mecanismo.

---

## Phase 4: User Story 2 - Autorización por rol (Priority: P1)

**Goal**: CAPTURISTA puede operar sobre personas (salvo eliminar) y consultar códigos
postales; eliminar personas y cualquier gestión de usuarios queda reservado a ADMIN.

**Independent Test**: Con un ADMIN y un CAPTURISTA autenticados, confirmar qué acepta y
qué rechaza (403) el sistema para cada rol sobre personas/códigos postales. (La cobertura
de "gestión de usuarios" de este mismo requisito, FR-008, se ejercita en la Fase 6 una vez
que existen los endpoints de `UsuarioController`; ver Dependencies).

### Tests for User Story 2 ⚠️

- [X] T038 [P] [US2] Extender
      `src/test/java/mx/personas/api/persona/PersonaControllerDeleteTest.java` (depende
      de T022): agregar caso `@WithMockUser` rol CAPTURISTA → `DELETE /api/personas/{id}`
      responde 403 `ACCESO_DENEGADO` (Acceptance Scenario US2 #2)
- [X] T039 [P] [US2] Nuevo integration test `RoleAuthorizationIT` en
      `src/test/java/mx/personas/api/integration/RoleAuthorizationIT.java`: login como
      ADMIN y como CAPTURISTA (sembrados por el propio test); CAPTURISTA puede crear,
      consultar, listar, actualizar persona y consultar CP/colonias (200); CAPTURISTA no
      puede eliminar persona (403); ADMIN puede todo lo anterior incluida eliminar
      (Acceptance Scenarios US2 #1 y #4)

### Implementation for User Story 2

- [X] T040 [US2] Anotar con `@PreAuthorize`: `hasRole('ADMIN')` en
      `PersonaController.eliminar(...)`
      (`src/main/java/mx/personas/api/persona/controller/PersonaController.java`); y
      `hasAnyRole('ADMIN','CAPTURISTA')` en el resto de métodos de `PersonaController`,
      `CodigoPostalController` y `ColoniaController` (FR-007, FR-008, FR-009)
- [X] T041 [US2] Agregar
      `@ExceptionHandler(AccessDeniedException.class)` a
      `src/main/java/mx/personas/api/common/error/GlobalExceptionHandler.java`:
      responde `403 ACCESO_DENEGADO` en el formato `ApiError` existente (FR-019)

**Checkpoint**: US1+US2 funcionales juntas — autenticación y separación de roles sobre
personas/códigos postales verificable de punta a punta.

---

## Phase 5: User Story 3 - Continuidad de sesión sin re-login (Priority: P2)

**Goal**: Renovar el token de acceso con el token de refresco, con rotación de un solo uso
y bloqueo inmediato si el usuario fue desactivado.

**Independent Test**: Login → usar access token → renovar con refresh token → usar el
nuevo access token → confirmar que el refresh token original ya no sirve.

### Tests for User Story 3 ⚠️

- [X] T042 [P] [US3] Extender `AuthControllerTest`
      (`src/test/java/mx/personas/api/auth/AuthControllerTest.java`, depende de T018):
      `POST /refresh` con token vigente → 200 con nuevo par de tokens; con token
      inválido/expirado/ya usado → 401; con token de un usuario desactivado → 401
      (FR-004, FR-005, FR-014, Acceptance Scenarios US3 #1-#3)
- [X] T043 [P] [US3] Nuevo integration test `RefreshTokenIT` en
      `src/test/java/mx/personas/api/integration/RefreshTokenIT.java`: login → refresh →
      nuevo access token funciona → reintentar refresh con el token original ya rotado →
      401 → desactivar el usuario y confirmar que un refresh posterior con un token aún
      vigente responde 401 mientras el access token ya emitido sigue siendo válido hasta
      su expiración natural (Acceptance Scenario US3 #2, Edge Case "refresh token ya
      usado")

### Implementation for User Story 3

- [X] T044 [US3] `RefreshRequestDTO` en
      `src/main/java/mx/personas/api/auth/dto/RefreshRequestDTO.java`
- [X] T045 [US3] `AuthService.refresh(...)`: busca por hash del token recibido, valida
      que exista, no esté `revocado`, no haya expirado, y que `usuario.activo`; si todo
      es válido marca la fila actual `revocado = true`, crea una fila nueva de
      `refresh_token`, y emite un nuevo access token (rotación en cada uso — FR-022); en
      cualquier otro caso lanza `401 NO_AUTENTICADO` sin distinguir el motivo (FR-005,
      FR-014)
- [X] T046 [US3] `AuthController`: `POST /refresh` (depende de T044, T045)

**Checkpoint**: US1+US2+US3 funcionales — sesiones largas sin re-login, con rotación
segura del token de refresco.

---

## Phase 6: User Story 4 - Gestión de usuarios del sistema (Priority: P3)

**Goal**: ADMIN puede crear, listar, desactivar y restablecer contraseña de usuarios del
sistema; un login usado jamás se reutiliza, ni siquiera desactivado.

**Independent Test**: Como ADMIN, crear un usuario, listarlo, desactivarlo, confirmar que
ya no puede autenticarse, e intentar reutilizar su login confirmando el rechazo 409.

### Tests for User Story 4 ⚠️

- [X] T047 [P] [US4] `@WebMvcTest` `UsuarioControllerTest` en
      `src/test/java/mx/personas/api/usuario/UsuarioControllerTest.java`: creación 201
      (sin contraseña/hash en la respuesta), listado 200 (sin contraseña/hash),
      desactivar 200, desactivar id inexistente 404, restablecer contraseña 204,
      restablecer contraseña id inexistente 404, y — con `@WithMockUser` rol CAPTURISTA —
      los 4 endpoints responden 403 `ACCESO_DENEGADO` (esto también valida el Acceptance
      Scenario US2 #3 / FR-008 para gestión de usuarios) (Acceptance Scenarios US4 #1-#3,
      #5)
- [X] T048 [P] [US4] Test unitario `UsuarioServiceTest` en
      `src/test/java/mx/personas/api/usuario/UsuarioServiceTest.java` (Mockito): crear
      con login ya usado por usuario activo → 409; crear con login ya usado por usuario
      desactivado → 409 (Edge Case); contraseña se persiste solo como hash BCrypt;
      restablecer contraseña invalida la anterior de inmediato (FR-011, FR-012, FR-016,
      FR-021)
- [X] T049 [P] [US4] Integration test `UsuarioLifecycleIT` en
      `src/test/java/mx/personas/api/integration/UsuarioLifecycleIT.java`: ADMIN crea un
      CAPTURISTA → login exitoso como CAPTURISTA → ADMIN lo desactiva → login posterior
      del CAPTURISTA falla 401 genérico → ADMIN intenta crear otro usuario con el mismo
      login → 409 → ADMIN restablece contraseña de un usuario activo y confirma que la
      contraseña anterior deja de funcionar (Acceptance Scenarios US4 #1-#5, Edge Case
      "usuario desactivado que intenta login")

### Implementation for User Story 4

- [X] T050 [P] [US4] DTOs `UsuarioCreateDTO`, `UsuarioResponseDTO`,
      `UsuarioResetPasswordDTO` en `src/main/java/mx/personas/api/usuario/dto/` (
      `UsuarioResponseDTO` nunca incluye contraseña ni hash — FR-016,
      contracts/usuarios-api.md)
- [X] T051 [US4] `UsuarioMapper` (MapStruct) en
      `src/main/java/mx/personas/api/usuario/mapper/UsuarioMapper.java` (depende de T050)
- [X] T052 [US4] `UsuarioService` en
      `src/main/java/mx/personas/api/usuario/service/UsuarioService.java`: `crear`
      (verifica duplicado de `login` activo o desactivado → `409
      USUARIO_LOGIN_DUPLICADO` antes de insertar, hashea contraseña con
      `PasswordEncoder`), `listar`, `desactivar` (`404` si no existe), `restablecerContrasena`
      (`404` si no existe, hashea la nueva contraseña e invalida la anterior de
      inmediato) (FR-010 a FR-012, FR-016, FR-021, depende de T008, T051)
- [X] T053 [US4] `UsuarioController` en
      `src/main/java/mx/personas/api/usuario/controller/UsuarioController.java`: `POST
      /api/usuarios`, `GET /api/usuarios`, `PATCH /api/usuarios/{id}/desactivar`, `PATCH
      /api/usuarios/{id}/contrasena`, todos `@PreAuthorize("hasRole('ADMIN')")` (FR-010,
      depende de T052)

**Checkpoint**: Las 4 historias de usuario funcionan juntas de punta a punta.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Verificación final de que el feature completo cumple spec y constitución

- [X] T054 Ejecutar manualmente `quickstart.md` de punta a punta contra la aplicación
      levantada localmente (bootstrap, US1-US4) y ajustar el propio `quickstart.md` si
      algún paso no coincide con el comportamiento real implementado
- [X] T055 Correr la suite completa (`./mvnw test` y `./mvnw verify` para los `*IT.java`)
      y confirmar 100% verde (FR-020, SC-005)
- [X] T056 [P] Auditar el repo (`grep -rn "X-API-Key\|api-key\|API_KEY"
      src/ specs/`) y confirmar que no queda ninguna referencia activa al mecanismo
      retirado fuera de la documentación histórica de `specs/001-.../` (FR-006a)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Sin dependencias — puede iniciar de inmediato
- **Foundational (Phase 2)**: Depende de Setup — BLOQUEA todas las historias de usuario
- **US1 (Phase 3)**: Depende solo de Foundational
- **US2 (Phase 4)**: Depende de Foundational; sus pruebas de rol sobre `PersonaController`
  reutilizan endpoints ya protegidos por US1 (T021-T033), pero no requiere que US1 esté
  "cerrada" formalmente, solo que la infraestructura JWT (Fase 2) exista
- **US3 (Phase 5)**: Depende de Foundational y de `AuthController`/`AuthService` creados
  en US1 (T034-T036), ya que agrega el endpoint hermano `/refresh` al mismo controller
- **US4 (Phase 6)**: Depende de Foundational (entidades/repositorios de `usuario`, T005-T009)
  y del mecanismo `@PreAuthorize`/`AccessDeniedException` establecido en US2 (T040-T041);
  su test T047 también cierra la cobertura de FR-008/US2-AC3 sobre gestión de usuarios
- **Polish (Phase 7)**: Depende de que todas las historias deseadas estén completas

### User Story Dependencies

- **US1 (P1)**: Ninguna dependencia sobre otras historias — es la base
- **US2 (P1)**: Se apoya en los endpoints ya protegidos por US1, pero es independientemente
  probable en lo que respecta a personas/códigos postales
- **US3 (P2)**: Se apoya en `AuthController`/`AuthService` de US1 (mismo archivo, nuevo
  endpoint) — no puede implementarse antes de US1
- **US4 (P3)**: Se apoya en el patrón de autorización de US2 — su prueba de rol
  (T047) es también la que cierra por completo el Acceptance Scenario US2 #3

### Within Each User Story

- Tests escritos y en rojo antes de la implementación correspondiente
- Modelos/DTOs antes de servicios; servicios antes de controllers
- Adaptación de la suite existente (US1) antes de eliminar `TestApiKey` (T037)

### Parallel Opportunities

- Todas las tareas [P] de Setup (T002, T003) en paralelo
- Dentro de Foundational: T005-T009 (modelos/repositorios) en paralelo entre sí; T015 y
  T016 en paralelo con lo anterior
- Dentro de US1: T018-T020 (tests nuevos) en paralelo; T021-T033 (adaptación de archivos
  existentes) todas en paralelo entre sí (archivos distintos)
- Dentro de US4: T047-T049 (tests) en paralelo; T050 en paralelo con los tests

---

## Parallel Example: User Story 1

```bash
# Tests nuevos de US1 en paralelo:
Task: "AuthControllerTest en src/test/java/mx/personas/api/auth/AuthControllerTest.java"
Task: "AuthServiceTest en src/test/java/mx/personas/api/auth/AuthServiceTest.java"
Task: "AuthIT en src/test/java/mx/personas/api/integration/AuthIT.java"

# Adaptación de la suite existente en paralelo (13 archivos, T021-T033):
Task: "Adaptar PersonaControllerCreateTest.java a TestJwt"
Task: "Adaptar PersonaControllerDeleteTest.java a TestJwt"
Task: "Adaptar CodigoPostalIT.java a TestJwt"
# ...resto de archivos listados arriba
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Fase 1: Setup
2. Completar Fase 2: Foundational (CRÍTICO — bloquea todas las historias)
3. Completar Fase 3: US1
4. **DETENER y VALIDAR**: probar US1 de forma independiente (quickstart.md §1-2)
5. Desplegar/demostrar si está listo

### Incremental Delivery

1. Setup + Foundational → base lista
2. + US1 → probar independientemente → demo (MVP: autenticación funciona)
3. + US2 → probar independientemente → demo (autorización por rol)
4. + US3 → probar independientemente → demo (continuidad de sesión)
5. + US4 → probar independientemente → demo (gestión de usuarios completa)
6. Cada historia agrega valor sin romper a las anteriores (suite siempre verde)

---

## Notes

- [P] tareas = archivos distintos, sin dependencias pendientes
- [Story] etiqueta cada tarea con su historia de usuario para trazabilidad
- Verificar que los tests fallan antes de implementar
- Detenerse en cada checkpoint para validar la historia de forma independiente
- El retiro de `X-API-Key` (T014) y su reemplazo en toda la suite (T021-T033, T037) es
  transversal a US1 pero se agrupa ahí por ser el requisito raíz que lo motiva (FR-006a)

---

## Phase 8: Convergence

- [X] T057 Agregar un test unitario directo para
      `GlobalExceptionHandler.handleAccessDenied(AccessDeniedException)` en
      `src/test/java/mx/personas/api/common/error/GlobalExceptionHandlerTest.java`,
      siguiendo el mismo patrón de un test por manejador ya usado en ese archivo, y
      asertando `403 ACCESO_DENEGADO` en el formato `ApiError` existente per FR-019 (partial)
- [X] T058 Agregar una prueba de regresión que confirme que una petición con el encabezado
      heredado `X-API-Key` (sin un JWT válido) se rechaza con `401 NO_AUTENTICADO` igual que
      cualquier llamada sin autenticación (p. ej. en `AuthIT.java` o
      `RoleAuthorizationIT.java`) per el Edge Case correspondiente de spec.md y FR-006a
      (partial)

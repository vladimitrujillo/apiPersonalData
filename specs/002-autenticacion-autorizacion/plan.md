# Implementation Plan: Autenticación y Autorización

**Branch**: `002-autenticacion-autorizacion` | **Date**: 2026-07-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-autenticacion-autorizacion/spec.md`

## Summary

Proteger toda la API existente (personas y códigos postales) con autenticación de
usuarios reales y autorización por rol, reemplazando por completo el actual filtro de
clave de API compartida (`X-API-Key`, sin identidad ni rol). Se introduce una nueva
población `Usuario del sistema` (login único y permanente, contraseña con hash BCrypt,
rol ADMIN/CAPTURISTA, estado activo/inactivo), separada de `persona`. El login emite un
token de acceso JWT (HS256, corta duración, sin estado en servidor) y un token de
refresco opaco de mayor duración (con estado en servidor, para poder rotarlo y
revocarlo). La autorización por rol se aplica con Spring Security method security
(`@PreAuthorize`). Un `ApplicationRunner` siembra el primer ADMIN de forma idempotente a
partir de variables de entorno, sin credenciales fijas en el código.

## Technical Context

**Language/Version**: Java 21 (existente)

**Primary Dependencies**: Spring Boot 3.3.5, Spring Security 6 (nueva), JJWT
`io.jsonwebtoken` (nueva, firma/parseo de JWT), Spring Data JPA (existente), Flyway
(existente), MapStruct (existente), springdoc-openapi 2.6.0 (existente, se añade esquema
`bearer`)

**Storage**: PostgreSQL (existente) — dos tablas nuevas: `usuario`, `refresh_token`

**Testing**: JUnit 5 + Mockito (existente), Testcontainers/PostgreSQL para IT
(existente), `spring-security-test` (nueva, para `@WithMockUser` en `@WebMvcTest`)

**Target Platform**: Linux server (existente, sin cambios)

**Project Type**: Aplicación web única desplegable (existente, Principio IV —
Simplicidad; no se introduce un servicio de autenticación separado)

**Performance Goals**: Sin metas nuevas más allá de las ya implícitas en el sistema
existente; la validación de un token de acceso es local (sin round-trip a BD),
solo el refresh consulta BD.

**Constraints**: Los endpoints existentes no cambian schema (spec FR-006); el mecanismo
de auth previo (`X-API-Key`) se retira por completo (spec FR-006a, Clarifications); la
suite de tests existente debe seguir en verde, adaptada para autenticarse (spec FR-020).

**Scale/Scope**: Población de usuarios del sistema pequeña (personal interno, decenas de
cuentas), no la escala de `persona` (padrón). No requiere paginación en el listado de
usuarios para el alcance de este feature.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Evaluación |
|---|---|
| I. Respetar lo Existente | PASA. Se sigue la estructura de paquetes por dominio (`controller/dto/mapper/model/repository/service`), el flujo controller→service→repository, MapStruct para DTOs, el `GlobalExceptionHandler`/`ApiError` existente (solo se agregan códigos), y el estilo de migraciones Flyway en SQL plano (ver `research.md`). |
| II. No Romper el Contrato | PASA CON EXCEPCIÓN JUSTIFICADA Y APROBADA. El schema de los endpoints de personas/códigos postales no cambia (FR-006). El retiro completo de `X-API-Key` es, en sentido estricto, un cambio de comportamiento para cualquier consumidor que dependiera de esa clave — pero es exactamente lo que la spec pide explícitamente (FR-006a) y fue aprobado en la sesión de `/speckit-clarify` del 2026-07-06 (ver spec.md § Clarifications), cumpliendo la cláusula de la constitución "cambios aditivos sí; breaking changes requieren aprobación explícita". |
| III. Test-First con Suite Siempre Verde | PASA (gate de proceso, no de diseño). `tasks.md` deberá ordenar: tests nuevos en rojo → implementación → suite completa en verde, y las tareas de adaptación de los tests existentes son explícitas (ver Fase 1 / `quickstart.md`). |
| IV. Privacidad por Diseño | PASA. Contraseñas solo como hash BCrypt; nunca en logs ni en respuestas (`UsuarioResponseDTO` no incluye el campo de contraseña ni su hash); ver `data-model.md`. |
| V. Migraciones Solo Aditivas y Versionadas | PASA. Nueva migración `V2__create_usuario_refresh_token.sql`, aditiva; `V1__create_schema.sql` no se toca. |
| VI. Identidad vs Contacto | PASA. `usuario.login` con `UNIQUE` global y sin borrado físico (solo `activo=false`), nunca reutilizable — ver `research.md` #9 y `data-model.md`. `usuario` no comparte tabla ni campo `correo` con `persona` (FR-015). *Nota: el hallazgo previo sobre `persona.curp` con unicidad solo-entre-activos (constitución v2.0.0, TODO pendiente) es un asunto separado de `persona`, fuera del alcance de este feature; no se toca en este cambio.* |
| Restricciones Adicionales | PASA. HTTPS/TLS es responsabilidad de despliegue (sin cambios); el acceso a datos personales queda sujeto a autenticación+autorización explícitas (el objetivo mismo de este feature); no se introducen microservicios ni colas (Simplicidad). |

Sin violaciones que requieran `Complexity Tracking`.

## Project Structure

### Documentation (this feature)

```text
specs/002-autenticacion-autorizacion/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── auth-api.md
│   └── usuarios-api.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

Aplicación Spring Boot única existente (Java 21, Maven). Se añaden dos dominios nuevos
(`auth`, `usuario`) siguiendo exactamente la convención de paquetes ya usada por
`persona` y `codigopostal`, y se modifica el paquete transversal `common.security`
(reemplazo del filtro de API key) y `common.config` (esquema `bearer` en OpenAPI).

```text
src/main/java/mx/personas/api/
├── auth/
│   ├── controller/
│   │   └── AuthController.java          # POST /login, POST /refresh
│   ├── dto/
│   │   ├── LoginRequestDTO.java
│   │   ├── TokenResponseDTO.java        # accessToken + refreshToken
│   │   └── RefreshRequestDTO.java
│   └── service/
│       └── AuthService.java             # login, refresh (rotación), validación de estado
├── usuario/
│   ├── controller/
│   │   └── UsuarioController.java       # POST/GET /api/usuarios, PATCH .../desactivar, .../contrasena
│   ├── dto/
│   │   ├── UsuarioCreateDTO.java
│   │   ├── UsuarioResponseDTO.java      # nunca incluye contraseña/hash
│   │   └── UsuarioResetPasswordDTO.java
│   ├── mapper/
│   │   └── UsuarioMapper.java           # MapStruct, igual que PersonaMapper
│   ├── model/
│   │   ├── Usuario.java
│   │   ├── Rol.java                     # enum ADMIN, CAPTURISTA
│   │   └── RefreshToken.java
│   ├── repository/
│   │   ├── UsuarioRepository.java
│   │   └── RefreshTokenRepository.java
│   ├── service/
│   │   └── UsuarioService.java          # crear, listar, desactivar, restablecer contraseña
│   └── bootstrap/
│       └── AdminBootstrapRunner.java    # ApplicationRunner, análogo a codigopostal/importer
└── common/
    ├── security/
    │   ├── JwtService.java              # nuevo: firma/parseo/validación HS256
    │   ├── JwtAuthenticationFilter.java  # nuevo: reemplaza a ApiKeyAuthFilter
    │   ├── SecurityConfig.java           # nuevo: SecurityFilterChain, PasswordEncoder, method security
    │   └── ApiKeyAuthFilter.java         # ELIMINADO (FR-006a)
    ├── error/
    │   └── ErrorCode.java                # + ACCESO_DENEGADO (403), + USUARIO_LOGIN_DUPLICADO (409)
    └── config/
        └── OpenApiConfig.java            # modificado: esquema "bearer" en vez de ApiKeyAuth

src/main/resources/db/migration/
└── V2__create_usuario_refresh_token.sql  # nueva, aditiva

src/test/java/mx/personas/api/
├── common/
│   └── TestJwt.java                      # nuevo helper, reemplaza el rol de TestApiKey
├── auth/                                  # tests nuevos: login, refresh, rotación, 401
├── usuario/                               # tests nuevos: alta, listado, desactivar, reset, 409
└── (persona|codigopostal)/...             # tests EXISTENTES: se ajustan para autenticarse
    en vez de enviar X-API-Key (mismo comportamiento funcional, nuevo mecanismo de auth)
```

**Structure Decision**: Un solo proyecto Maven/Spring Boot (sin cambios de topología).
Dos paquetes de dominio nuevos (`auth`, `usuario`) al mismo nivel que `persona` y
`codigopostal`, más cambios acotados en `common.security`/`common.config`/`common.error`.
No se crean módulos, servicios ni artefactos desplegables adicionales.

## Complexity Tracking

*Sin violaciones de la Constitution Check; esta sección no aplica.*

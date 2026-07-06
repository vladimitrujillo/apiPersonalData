# Data Model: Autenticación y Autorización

Basado en el spec (Key Entities) y en las decisiones de `research.md`. Los tipos de
columna son indicativos para PostgreSQL/Flyway; se detallan en la migración durante la
implementación (`tasks.md`), no aquí.

## usuario

Representa a un operador del sistema (no a una persona del padrón). Spec FR-001,
FR-007, FR-009 a FR-017; Constitución Principio VI.

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | UUID (PK) | Generado por el sistema al crear |
| `login` | text | Requerido; **único de forma global y permanente** (`UNIQUE` simple, sin excepción por `activo` — research.md §9); es la credencial de identidad (FR-011). No se deriva de datos de contacto de ninguna persona (FR-015) |
| `password_hash` | text | Requerido; hash BCrypt (nunca texto plano, nunca expuesto — FR-016) |
| `nombre` | text | Requerido; nombre para mostrar del operador |
| `rol` | varchar(20), `CHECK IN ('ADMIN','CAPTURISTA')` | Requerido; exactamente un rol por usuario (research.md §8) |
| `activo` | boolean, `NOT NULL DEFAULT true` | `true` = puede autenticarse; `false` = desactivado (FR-013). **Nunca se borra la fila** — solo se desactiva, para preservar la unicidad permanente del `login` |
| `created_at` / `updated_at` | timestamp | Auditoría estándar, igual que `persona` |

**Transiciones de estado**: `activo = true` → `activo = false` (desactivación, FR-013).
No existe transición inversa expuesta por la API en este feature (no hay
"reactivar"; si se necesita en el futuro, es ampliación de alcance). No hay operación de
borrado físico expuesta ni prevista.

**Validaciones cruzadas**: crear un `usuario` con un `login` que ya existe (activo o
desactivado) se rechaza con `409 USUARIO_LOGIN_DUPLICADO` (FR-012), verificado en el
`service` antes de insertar, reforzado por el `UNIQUE` de BD ante condiciones de carrera
(mismo patrón que `persona.correo`/`persona.curp` en `specs/001.../research.md` §2, pero
aquí sin condición `WHERE activo = true`).

## refresh_token

Representa una sesión de renovación vigente o ya usada de un `usuario`. Spec FR-004,
FR-005, FR-014, FR-022; research.md §3.

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | UUID (PK) | Generado por el sistema |
| `usuario_id` | UUID (FK → usuario) | Requerido |
| `token_hash` | text | Requerido; SHA-256 del valor opaco entregado al cliente (el valor en claro no se persiste — research.md §3) |
| `expira_en` | timestamptz | Requerido; vida útil mayor que el token de acceso (spec Assumptions) |
| `revocado` | boolean, `NOT NULL DEFAULT false` | `true` cuando el token ya fue usado para renovar (rotación, FR-022 de la spec/decisión D2) o cuando su `usuario` fue desactivado |
| `created_at` | timestamp | Auditoría estándar |

**Transiciones de estado**: `revocado = false` → `revocado = true`, ya sea (a) al usarse
exitosamente para renovar (rotación: se crea una fila nueva y esta se marca revocada), o
(b) de forma lógica en tiempo de validación cuando `usuario.activo = false` (no requiere
un `UPDATE` masivo; el `service` de refresh valida `usuario.activo` además de `revocado`
y `expira_en` en cada intento — FR-014).

**Validaciones cruzadas**: un intento de refresh con un token cuyo hash no exista, o
exista pero `revocado = true`, o `expira_en` ya pasó, o cuyo `usuario.activo = false`,
se rechaza con `401 NO_AUTENTICADO` (FR-005), sin distinguir cuál de esos casos ocurrió
(mismo principio de no revelar información que en login — FR-003).

## Rol (enum, no es tabla)

`ADMIN` (acceso total, incluida gestión de usuarios) | `CAPTURISTA` (alta, consulta,
listado y actualización de personas; consulta de códigos postales). Representado en BD
como `usuario.rol` (varchar + `CHECK`, research.md §8) y en Java como
`enum Rol { ADMIN, CAPTURISTA }` mapeado a la autoridad de Spring Security
`ROLE_<nombre>`.

## Resumen de relaciones

```text
usuario 1---N refresh_token (FK usuario_id; una fila nueva por cada login y por cada
                              rotación de refresh)
```

`usuario` no tiene relación de esquema con `persona` (FR-015): son poblaciones
completamente separadas, sin tabla ni columna compartida.

## Contraste explícito con el modelo de `persona` (Constitución Principio VI)

| | `usuario.login` | `persona.correo` |
|---|---|---|
| Unicidad | Global, incluso entre desactivados | Solo entre activos |
| Reutilizable tras desactivar/eliminar | Nunca | Sí (otra persona activa puede usarlo) |
| Es credencial de autenticación | Sí | No (FR-015; nunca se usa para login) |

Esta tabla documenta explícitamente la distinción identidad-vs-contacto que la
constitución exige mantener visible en el diseño, para que una futura feature no
"unifique por error" ambos modelos de unicidad.

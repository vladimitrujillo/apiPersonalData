# Data Model: Catálogo de Profesiones y Asignación a Personas

## Tabla `profesion`

Catálogo administrado de profesiones válidas.

| Columna | Tipo | Notas |
|---|---|---|
| `id` | `BIGSERIAL PK` | |
| `nombre` | `VARCHAR(80) NOT NULL` | Único, comparación insensible a mayúsculas/acentos (índice funcional `unaccent(lower(nombre))`, research.md §1) |
| `descripcion` | `TEXT` | Nullable/opcional |
| `activo` | `BOOLEAN NOT NULL DEFAULT true` | `false` = desactivada; no se elimina físicamente (FR-007/FR-008) |
| `creado_por` | `UUID` | `Auditable` — FK a `usuario(id)`, nullable |
| `created_at` | `TIMESTAMPTZ NOT NULL` | `Auditable` |
| `actualizado_por` | `UUID` | `Auditable` — FK a `usuario(id)`, nullable |
| `updated_at` | `TIMESTAMPTZ NOT NULL` | `Auditable` |

**Índices**:
- `ux_profesion_nombre_unaccent` — único, funcional, sobre
  `unaccent_immutable(lower(nombre))` (research.md §1-2).

**Semilla**: una fila con `nombre = 'Mecánico'`, `activo = true`, inserción
idempotente (`WHERE NOT EXISTS` o `ON CONFLICT DO NOTHING` sobre el índice
único) para que la migración sea segura de re-ejecutar en un entorno que ya
la tenga (aunque Flyway no re-ejecuta migraciones aplicadas, la propia
sentencia debe ser idempotente por buena práctica y por si se recrea el
entorno desde cero).

**Transiciones de estado**: `activo: true → false` (desactivar, FR-007),
`false → true` (reactivar, FR-007). Ambas solo por ADMIN. Desactivar NUNCA
modifica `persona_profesion` (FR-008).

## Tabla `persona_profesion`

Asignación de una Profesión del catálogo a una Persona.

| Columna | Tipo | Notas |
|---|---|---|
| `id` | `UUID PK` | |
| `persona_id` | `UUID NOT NULL` | FK a `persona(id)` |
| `profesion_id` | `BIGINT NOT NULL` | FK a `profesion(id)` |
| `fecha_desde` | `DATE NOT NULL DEFAULT CURRENT_DATE` | Fecha desde la cual aplica (FR-012) |
| `cedula` | `VARCHAR(30)` | Nullable/opcional — cédula o certificado, texto libre |
| `activo` | `BOOLEAN NOT NULL DEFAULT true` | `false` = retirada; no se elimina físicamente (FR-015) |
| `creado_por` | `UUID` | `Auditable` |
| `created_at` | `TIMESTAMPTZ NOT NULL` | `Auditable` |
| `actualizado_por` | `UUID` | `Auditable` |
| `updated_at` | `TIMESTAMPTZ NOT NULL` | `Auditable` |

**Índices**:
- `ux_persona_profesion_activa` — único, parcial, sobre `(persona_id,
  profesion_id) WHERE activo = true` (research.md §3): como mucho una
  asignación activa por par persona-profesión, sin límite de episodios
  retirados acumulados.
- `ix_persona_profesion_persona_id` — sobre `persona_id` (consultar
  profesiones de una persona).
- `ix_persona_profesion_profesion_id` — sobre `profesion_id` (directorio por
  profesión).

**Transiciones de estado**: `activo: true → false` (retirar, FR-015). Nunca
`false → true` — reasignar la misma profesión crea una fila nueva
(research.md §4, decisión de `/speckit-clarify`), nunca reactiva una
existente.

**Reglas de integridad de negocio** (aplicadas en el service, no solo en el
esquema):
- No se puede crear una fila con `persona_id` de una persona con
  `persona.activo = false` (FR-014).
- No se puede crear una fila con `profesion_id` de una profesión con
  `profesion.activo = false` (FR-009).

## Relaciones

```text
Persona (1) ──< (N) PersonaProfesion (N) >── (1) Profesion
```

- Una `Persona` puede tener 0 o varias `PersonaProfesion` (una por profesión
  distinta simultáneamente activa; puede acumular varias históricas de la
  misma profesión).
- Una `Profesion` puede estar referenciada por 0 o varias `PersonaProfesion`
  (muchas personas).
- `PersonaProfesion` es la tabla de asociación, con atributos propios
  (`fecha_desde`, `cedula`, `activo`) — no es una simple tabla puente N:M sin
  datos.

## Auditoría existente reutilizada

- `Profesion` y `PersonaProfesion` extienden `Auditable`
  (`mx.personas.api.common.audit.Auditable`) para "quién/cuándo" (research.md
  §5).
- Cada asignación (`FR-012`) y retiro (`FR-015`) de una profesión a una
  persona se registra además como una entrada `MODIFICACION` en
  `persona_historial` (tabla ya existente de `003-auditoria-personas`), vía
  un método nuevo en `HistorialDiffService` — igual que ya se registran los
  cambios de dirección de una persona.

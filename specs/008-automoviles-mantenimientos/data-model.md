# Data Model: Automóviles de Personas y Mantenimientos

## `automovil`

| Columna | Tipo | Notas |
|---|---|---|
| `id` | `UUID PK` | `gen_random_uuid()` por defecto |
| `persona_id` | `UUID NOT NULL` | `REFERENCES persona(id)` |
| `marca` | `VARCHAR(60) NOT NULL` | |
| `modelo` | `VARCHAR(60) NOT NULL` | |
| `anio` | `SMALLINT NOT NULL` | `CHECK (anio >= 1900)`; límite superior validado en el service (research.md §7) |
| `color` | `VARCHAR(40)` | nullable |
| `placas` | `VARCHAR(10) NOT NULL` | única solo entre `activo = true` (índice parcial), editable |
| `vin` | `VARCHAR(17)` | nullable; única global (índice simple, sin condición); inmutable tras el alta. `CHAR(17)` se descartó durante la implementación: la validación de esquema de Hibernate compara por código JDBC (no por el DDL crudo) y no reconoce `CHAR` vía `columnDefinition`; `VARCHAR(17)` es funcionalmente equivalente para un VIN (evita además el padding con espacios de `CHAR`) |
| `activo` | `BOOLEAN NOT NULL DEFAULT true` | borrado lógico |
| columnas de `Auditable` | | `creado_por`, `created_at`, `actualizado_por`, `updated_at` |

**Índices**:
- `ux_automovil_placas_activo ON automovil (placas) WHERE activo = true`
- `ux_automovil_vin ON automovil (vin)` (único simple; `NULL` no colisiona consigo mismo en PostgreSQL, permitiendo múltiples automóviles sin VIN)
- `ix_automovil_persona_id ON automovil (persona_id)`

**Transiciones de estado**: `activo: true → false` (baja, `AutomovilController.eliminar`) ⇄ `false → true` (restaurar, solo ADMIN). Nunca hay borrado físico.

## `mantenimiento`

| Columna | Tipo | Notas |
|---|---|---|
| `id` | `UUID PK` | `gen_random_uuid()` por defecto |
| `automovil_id` | `UUID NOT NULL` | `REFERENCES automovil(id)` |
| `descripcion` | `TEXT NOT NULL` | |
| `fecha` | `DATE NOT NULL` | `CHECK (fecha <= CURRENT_DATE)` |
| `kilometraje` | `INT NOT NULL` | `CHECK (kilometraje >= 0)` |
| `mecanico_id` | `UUID` | nullable; `REFERENCES persona(id)`; elegibilidad validada en el service (research.md §2-3), no con FK condicional |
| `costo_total` | `NUMERIC(12,2) NOT NULL` | `CHECK (costo_total >= 0)` |
| `activo` | `BOOLEAN NOT NULL DEFAULT true` | borrado lógico |
| columnas de `Auditable` | | `creado_por`, `created_at`, `actualizado_por`, `updated_at` |

**Índices**:
- `ix_mantenimiento_automovil_id_fecha ON mantenimiento (automovil_id, fecha DESC)` (historial paginado, orden por defecto)
- `ix_mantenimiento_mecanico_id ON mantenimiento (mecanico_id)`

**Transiciones de estado**: `activo: true → false` (baja, solo ADMIN) ⇄ `false → true` (restaurar, solo ADMIN). Igual que `automovil`, sin borrado físico.

**Regla de consistencia (aplicada en el service, no en BD)**: al crear o
editar, `kilometraje` DEBE ser `>=` el `kilometraje` del mantenimiento
`activo = true` con la `fecha` más reciente del mismo `automovil_id`
(excluyendo, en `update`, el propio registro que se edita). Ver
research.md §6.

## `pieza_cambiada`

| Columna | Tipo | Notas |
|---|---|---|
| `id` | `UUID PK` | `gen_random_uuid()` por defecto |
| `mantenimiento_id` | `UUID NOT NULL` | `REFERENCES mantenimiento(id) ON DELETE CASCADE` (composición pura vía JPA `orphanRemoval`; el `ON DELETE CASCADE` es una segunda red de seguridad a nivel BD) |
| `nombre` | `VARCHAR(120) NOT NULL` | |
| `numero_parte` | `VARCHAR(40)` | nullable |
| `costo` | `NUMERIC(12,2)` | nullable; `CHECK (costo IS NULL OR costo >= 0)` |

Sin columnas de auditoría propias (research.md §5): su ciclo de vida está
completamente gobernado por el `Mantenimiento` al que pertenece.

## Relaciones

```text
Persona (existente, 001)
  └─< Automovil (persona_id)
        └─< Mantenimiento (automovil_id)
              ├─< PiezaCambiada (mantenimiento_id, composición)
              └─> Persona (mecanico_id, opcional — 007: requiere profesión "Mecánico" activa)
```

- `Persona 1—N Automovil`: una persona puede tener 0..N automóviles; un automóvil pertenece a exactamente una persona.
- `Automovil 1—N Mantenimiento`: un automóvil puede tener 0..N mantenimientos.
- `Mantenimiento 1—N PiezaCambiada`: composición; las piezas no existen sin su mantenimiento.
- `Mantenimiento N—0..1 Persona` (como mecánico): referencia opcional, validada dinámicamente (no es una relación de propiedad).

## Reutilización de auditoría (research.md §4)

Ni `Automovil` ni `Mantenimiento` tienen su propia tabla de historial. Todas
las operaciones de alta/edición/baja/restauración se registran en
`persona_historial`, atribuidas a la `Persona` dueña del automóvil (para
`Mantenimiento`, se resuelve `mantenimiento.automovil.persona`), extendiendo
`HistorialDiffService` con nuevos métodos `serializar*`, exactamente como ya
hace `persona_profesion` en `007-profesiones-personas`.

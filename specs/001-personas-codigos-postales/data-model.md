# Data Model: Gestión de Personas y Catálogo de Códigos Postales

Basado en el spec (Key Entities), en las decisiones de `research.md`, y en el modelo de
datos de referencia provisto (tablas `persona`, `direccion`, `cp_catalogo`). Los tipos de
columna son indicativos para PostgreSQL/Flyway; se detallan en migraciones durante la
implementación (`tasks.md`), no aquí.

## persona

Representa a un individuo registrado en el sistema (spec FR-001 a FR-012, FR-023).

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | UUID (PK) | Generado por el sistema al crear (Assumptions) |
| `nombres` | text | Requerido |
| `apellidos` | text | Requerido |
| `fecha_nacimiento` | date | Requerido; MUST NOT ser posterior a la fecha actual (FR-008) |
| `sexo` | enum/text | Requerido |
| `curp` | varchar(18) | Requerido; formato oficial (research.md §4); único entre personas activas (FR-007, índice único parcial `WHERE activo = true`, research.md §2) |
| `rfc` | varchar(13) | Requerido; formato oficial persona física (research.md §4) |
| `correo` | text | Requerido; formato de correo válido (FR-009); único entre personas activas (FR-006, índice único parcial) |
| `telefono` | varchar(10) | Requerido; 10 dígitos numéricos nacionales MX (FR-010) |
| `activo` | boolean, `NOT NULL DEFAULT true` | Borrado lógico (research.md §1): `true` = activa, `false` = eliminada lógicamente (FR-005). No se expone en listados/consultas activas cuando es `false` (FR-012) |
| `created_at` / `updated_at` | timestamp | Auditoría estándar |

**Transiciones de estado**: `activo = true` → `activo = false` (borrado lógico, FR-005). No
existe transición inversa expuesta por la API en este feature (no hay "restaurar" en el
spec). Una persona con `activo = false` no admite actualización (FR-012 / edge case:
responde 404).

## direccion

Datos de ubicación de una persona (spec: FR-001, FR-019 a FR-022). Tabla propia,
relacionada 1:N con `persona` a nivel de esquema (ver `research.md` §3c sobre alcance
actual: una sola dirección vigente por persona en este feature).

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | UUID (PK) | Generado por el sistema |
| `persona_id` | UUID (FK → persona) | Requerido |
| `calle` | text | Requerido |
| `numero` | text | Requerido (permite alfanumérico, p. ej. "123-A") |
| `colonia` | text | Snapshot de texto; requerido. Si `pais = MX` y se da `codigo_postal`, MUST pertenecer a la lista de colonias de ese CP en `cp_catalogo` al momento de guardar (FR-021) |
| `municipio` | text | Snapshot de texto. Si `pais = MX` y `codigo_postal` es válido, se autocompleta desde `cp_catalogo` (FR-020) |
| `estado` | text | Snapshot de texto. Misma regla que `municipio` (FR-020) |
| `codigo_postal` | varchar(5) | Si `pais = MX`, MUST existir en `cp_catalogo` (FR-019); si `pais != MX`, se acepta como texto libre sin validar (FR-022) |
| `pais` | text | Requerido; determina si aplica validación contra `cp_catalogo` |
| `cp_catalogo_id` | bigint/UUID (FK → cp_catalogo), **nullable** | Referencia a la fila específica de `cp_catalogo` usada para validar/autocompletar (research.md §3c). `NULL` cuando `pais != MX` |
| `created_at` / `updated_at` | timestamp | Auditoría estándar |

**Nota de alcance**: aunque el esquema modela `direccion` como 1:N respecto a `persona`
(dejando espacio para historial futuro), el spec de este feature (FR-001, FR-019 a FR-022)
solo requiere una dirección vigente por persona; el `service` mantiene una única fila
vigente por persona. Un historial de direcciones expuesto por la API sería una ampliación
fuera de alcance de este feature.

## cp_catalogo

Catálogo Nacional de Códigos Postales de SEPOMEX, modelado como tabla plana que refleja
la estructura del archivo fuente (spec FR-013 a FR-018; research.md §3).

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | bigint (PK) | Interno |
| `codigo_postal` | varchar(5) | Requerido; exactamente 5 dígitos numéricos (FR-014); indexado (`ix_cp_catalogo_codigo_postal`, research.md §3b) para consulta exacta (FR-013) |
| `estado` | text | Requerido |
| `municipio` | text | Requerido |
| `asentamiento` | text | Requerido; nombre de la colonia/asentamiento; indexado en conjunto con `estado`/`municipio` (`ix_cp_catalogo_estado_municipio_asenta`) para búsqueda por coincidencia parcial (FR-016) |
| `tipo_asentamiento` | text | Requerido (p. ej. "Colonia", "Fraccionamiento", "Ejido") |
| `id_asenta_cpcons` | text/int | Identificador consecutivo de asentamiento dentro del CP, tal como lo provee el archivo fuente de SEPOMEX; forma parte de la clave de importación idempotente |

**Restricción de unicidad / clave de importación**: `UNIQUE (codigo_postal, id_asenta_cpcons)`
— es la clave usada por el upsert idempotente del proceso de importación (research.md §3).

## Resumen de relaciones

```text
persona 1---N direccion (esquema 1:N; alcance actual = 1 dirección vigente por persona)
direccion N---1 cp_catalogo (FK opcional cp_catalogo_id; NULL cuando país != MX)
```

`cp_catalogo` es una tabla plana (no normalizada en códigos postales + colonias separadas):
cada fila ya representa un asentamiento específico dentro de un código postal, por lo que
"las colonias de un CP" (US3) se obtienen agrupando/filtrando `cp_catalogo` por
`codigo_postal`.

## Validaciones cruzadas (no expresables como constraint simple de columna)

- **Unicidad condicional** de `correo` y `curp` de `persona`: índice único parcial
  `WHERE activo = true` (research.md §2).
- **Fecha de nacimiento no futura**: validación Bean Validation (`@PastOrPresent`) en el
  DTO de entrada, reforzada en el `service`.
- **CP existente y colonia perteneciente al CP**: validación en el `service` de `direccion`,
  consultando `cp_catalogo` por `codigo_postal` y verificando que `colonia` esté entre los
  valores de `asentamiento` de esas filas; no se implementa como FK física obligatoria
  porque el catálogo puede reimportarse y direcciones no-MX no tienen fila de catálogo
  (research.md §3c).

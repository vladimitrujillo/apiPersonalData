# Data Model: Automatización de la Actualización del Catálogo SEPOMEX

Extiende `specs/001-personas-codigos-postales/data-model.md` (`cp_catalogo`). No se
modifica `cp_catalogo` en sí (mismas columnas, mismo `UNIQUE (codigo_postal,
id_asenta_cpcons)`); se añade una tabla nueva para la bitácora.

## catalogo_importacion (tabla nueva)

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | UUID (PK) | Generado por el sistema |
| `origen` | varchar(20), `CHECK IN ('PROGRAMADA','MANUAL')` | Requerido |
| `usuario_id` | UUID (FK → usuario, **nullable**) | Presente solo cuando `origen = MANUAL`; `NULL` para corridas programadas (spec FR-007) |
| `archivo` | text | Nombre/identificador del archivo procesado (o intentado) |
| `fecha_inicio` | timestamptz, `NOT NULL DEFAULT now()` | Momento en que inició la corrida |
| `duracion_ms` | bigint | Duración total de la corrida, en milisegundos |
| `insertados` | int, `NOT NULL DEFAULT 0` | Filas insertadas (research.md §2) |
| `actualizados` | int, `NOT NULL DEFAULT 0` | Filas actualizadas con cambio real |
| `sin_cambio` | int, `NOT NULL DEFAULT 0` | Filas que ya tenían esos valores |
| `rechazados` | int, `NOT NULL DEFAULT 0` | Filas individualmente inválidas, omitidas (FR-012) |
| `estado` | varchar(30), `CHECK IN ('EXITO','ERROR','RECHAZADA_CONCURRENCIA')` | Requerido |
| `detalle_error` | text, **nullable** | Presente cuando `estado IN ('ERROR','RECHAZADA_CONCURRENCIA')` |

**Índice**: `CREATE INDEX ix_catalogo_importacion_fecha ON catalogo_importacion
(fecha_inicio DESC);` — soporta `GET .../importaciones` ordenado del más reciente al
más antiguo (mismo criterio que `ix_persona_historial_persona_fecha` de `specs/003`).

**Inmutabilidad**: como el historial de `specs/003`, es una tabla de solo `INSERT` —
ningún endpoint la actualiza o borra.

**Transiciones de estado**: cada fila se crea una sola vez, ya con su `estado` final
(`EXITO`, `ERROR` o `RECHAZADA_CONCURRENCIA`); no hay una fila que empiece en un estado
y transicione a otro (a diferencia de `persona`, aquí no hay una corrida "en progreso"
visible en la bitácora — se registra al terminar, sea cual sea el resultado).

## Resumen de relaciones

```text
usuario 1---N catalogo_importacion (usuario_id, solo para origen MANUAL)
```

Sin relación de esquema con `persona`/`direccion` ni con `persona_historial` — dominios
independientes (`codigopostal` vs. `persona`).

## Distinción con `persona_historial` (specs/003)

| | `persona_historial` | `catalogo_importacion` |
|---|---|---|
| Qué audita | Cambios a un registro de negocio (`persona`) | Ejecuciones de un proceso batch (importación del catálogo) |
| Cardinalidad del autor | Siempre un `usuario` (toda escritura de persona pasa por un endpoint autenticado) | `usuario_id` nulo cuando el origen es programado (sin operador humano) |
| Contenido | Diff de campos (`cambios` JSONB) | Resumen agregado (conteos) + estado |

Ambas comparten el mismo espíritu (bitácora inmutable, solo `INSERT`) pero modelan
dominios distintos; no se unifican en una sola tabla porque sus filas no son
comparables (una es por-persona, la otra es por-corrida-de-proceso).

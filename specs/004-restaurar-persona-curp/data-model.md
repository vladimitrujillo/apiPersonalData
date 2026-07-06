# Data Model: Restaurar Personas y Unicidad de CURP Global

Extiende `specs/001-personas-codigos-postales/data-model.md`. No se agregan entidades ni
tablas nuevas; se modifica una regla de unicidad de `persona` y se documentan los datos
expuestos por los endpoints nuevos.

## persona (regla de unicidad modificada)

| Campo | Regla anterior (specs/001) | Regla nueva (este feature) |
|---|---|---|
| `curp` | Única entre personas activas (índice parcial `ux_persona_curp_activo`) | Única de forma **global** (restricción `uq_persona_curp`, sin excepción por `activo` — D2, research.md §2) |
| `correo` | Única entre personas activas (índice parcial `ux_persona_correo_activo`) | **Sin cambio** (D3 ya vigente — research.md §1) |

Ningún otro campo de `persona` cambia. La fila conserva todos sus datos y su dirección
intactos durante todo el ciclo eliminar→restaurar (FR-005), porque la eliminación y la
restauración solo tocan el flag `activo` (y, desde `003`, los campos de auditoría
`actualizado_por`/`actualizado_en`).

## Reglas de conflicto (dónde se decide cada 409)

| Operación | Campo en conflicto | Contra qué se compara | Resultado |
|---|---|---|---|
| Crear / Actualizar | `curp` | Cualquier otra fila con esa CURP (activa o eliminada) | Activa → `409 PERSONA_CURP_DUPLICADO` (sin cambio). Eliminada → `409 PERSONA_CURP_ELIMINADA` (nuevo, accionable, incluye `id` del registro eliminado) |
| Crear / Actualizar | `correo` | Otra persona **activa** con ese correo | `409 PERSONA_CORREO_DUPLICADO` (sin cambio; nunca conflictúa contra una persona eliminada — D3) |
| Restaurar | `correo` | Otra persona **activa** con el correo de la persona a restaurar | `409 PERSONA_CORREO_DUPLICADO`, `detalles` incluye el `id` de esa persona activa (FR-009) |
| Restaurar | `curp` | — | Nunca conflictúa: la unicidad global (D2) hace estructuralmente imposible que otra fila tenga esa CURP mientras esta estaba eliminada (FR-010) |
| Restaurar | (estado) | La propia persona ya está `activo = true` | `409 PERSONA_YA_ACTIVA` (FR-007, código ya introducido por `specs/003`) |
| Restaurar | (existencia) | Ningún registro con ese `id` | `404 PERSONA_NO_ENCONTRADA` (FR-008) |

## Datos expuestos en mensajes accionables (FR-004, FR-009)

Ambos casos siguen el esquema ya existente de `ApiError.CampoError { campo, motivo }`
(sin cambio de contrato — Principio II): el `motivo` es un texto que **solo** incluye el
`id` (UUID) del registro referido — nunca nombres, CURP/correo/teléfono ni dirección de
ese registro, para no filtrar datos personales a quien no tiene permiso de consultarlos
(spec Edge Cases).

## `GET /api/personas/eliminadas` (vista nueva)

Mismo shape de persona que `GET /api/personas/{id}` (incluye los campos de auditoría de
`specs/003` si ya están implementados), paginado igual que el listado general, pero
filtrando `activo = false` en vez de `activo = true`. Sin filtros de nombre/municipio/
estado en el alcance de este feature (no pedidos por la spec); puede ampliarse después
si se necesita.

## Resumen de relaciones

Sin cambios respecto a `specs/001-personas-codigos-postales/data-model.md` y
`specs/003-auditoria-personas/data-model.md` (si ya implementado): `persona 1---N
direccion`; `persona 1---N persona_historial` (vía `003`, para la nueva operación
`RESTAURACION`).

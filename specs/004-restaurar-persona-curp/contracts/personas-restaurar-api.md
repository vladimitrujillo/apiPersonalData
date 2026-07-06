# Contrato: Restauración, CURP Global y Vista de Eliminados

Extiende `specs/001-personas-codigos-postales/contracts/personas-api.md`. Todas las
rutas requieren `Authorization: Bearer <accessToken>` (`002`). Errores en el formato de
`specs/001-personas-codigos-postales/contracts/error-format.md`.

## POST /api/personas y PATCH /api/personas/{id} (regla de CURP ampliada)

Sin cambio de request/response. Nuevo comportamiento de error cuando la CURP enviada ya
pertenece a otro registro:

- Si ese registro está **activo**: `409 PERSONA_CURP_DUPLICADO` (sin cambio).
- Si ese registro está **eliminado lógicamente**: `409 PERSONA_CURP_ELIMINADA` (nuevo).

```json
{
  "codigo": "PERSONA_CURP_ELIMINADA",
  "mensaje": "Existe un registro eliminado con este CURP; un ADMIN puede restaurarlo",
  "detalles": [
    { "campo": "curp", "motivo": "Registro eliminado con id 3fa85f64-5717-4562-b3fc-2c963f66afa6" }
  ]
}
```

El conflicto de `correo` (activo-contra-activo) no cambia.

## POST /api/personas/{id}/restaurar

Restaura una persona eliminada lógicamente (US3), solo ADMIN.

**200 OK** → cuerpo: la persona restaurada, con sus datos y dirección exactamente como
estaban antes de eliminarse (mismo shape que `GET /api/personas/{id}`).

**Errores**:
- `403 ACCESO_DENEGADO` — rol distinto de ADMIN (FR-006).
- `404 PERSONA_NO_ENCONTRADA` — el `id` no existe (FR-008).
- `409 PERSONA_YA_ACTIVA` — la persona ya está activa (FR-007).
- `409 PERSONA_CORREO_DUPLICADO` — el correo de la persona a restaurar ya pertenece a
  otra persona activa; `detalles` incluye el `id` de esa persona (FR-009):

  ```json
  {
    "codigo": "PERSONA_CORREO_DUPLICADO",
    "mensaje": "Ya existe una persona activa registrada con este correo electrónico",
    "detalles": [
      { "campo": "correo", "motivo": "En uso por la persona activa con id 9c858f9e-8a5b-4b1a-9c1e-000000000000" }
    ]
  }
  ```

La CURP nunca produce un conflicto en este endpoint (FR-010).

## GET /api/personas/eliminadas

Vista paginada y dedicada de personas eliminadas lógicamente, solo ADMIN (US4).

**200 OK**:

```json
{
  "contenido": [ { "...persona...": "...", "activo": false } ],
  "pagina": 0,
  "tamanoPagina": 20,
  "totalElementos": 2,
  "totalPaginas": 1
}
```

Solo incluye personas con `activo = false`. Ninguna persona activa aparece en esta
vista (FR-012).

**Errores**: `403 ACCESO_DENEGADO` — rol distinto de ADMIN (FR-013).

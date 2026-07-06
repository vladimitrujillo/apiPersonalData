# Contrato: Auditoría, Historial y Restauración de Personas

Extiende `specs/001-personas-codigos-postales/contracts/personas-api.md`. Todas las
rutas requieren `Authorization: Bearer <accessToken>` (`002`). Errores en el formato de
`specs/001-personas-codigos-postales/contracts/error-format.md`.

## GET /api/personas/{id} (respuesta ampliada)

Sin cambios de ruta ni de request. La respuesta **añade** (de forma aditiva, Principio
II) los campos de auditoría de la persona y de su dirección (FR-003):

```json
{
  "...": "...campos existentes sin cambios...",
  "creadoPor": "string (login) | null",
  "creadoEn": "string (ISO-8601)",
  "modificadoPor": "string (login) | null",
  "modificadoEn": "string (ISO-8601)",
  "direccion": {
    "...": "...campos existentes sin cambios...",
    "creadoPor": "string (login) | null",
    "creadoEn": "string (ISO-8601)",
    "modificadoPor": "string (login) | null",
    "modificadoEn": "string (ISO-8601)"
  }
}
```

`creadoPor`/`modificadoPor` son `null` únicamente para registros creados antes de este
feature (spec Assumptions). `GET /api/personas` (listado) **no** cambia — sigue sin
estos campos (FR-004).

## GET /api/personas/{id}/historial

Historial paginado de una persona, solo ADMIN (US2, FR-011, FR-012).

**200 OK**:

```json
{
  "contenido": [
    {
      "fecha": "string (ISO-8601)",
      "usuario": "string (login)",
      "operacion": "CREACION | MODIFICACION | ELIMINACION | RESTAURACION",
      "cambios": [
        { "campo": "string", "valorAnterior": "string | null", "valorNuevo": "string | null" }
      ]
    }
  ],
  "pagina": 0,
  "tamanoPagina": 20,
  "totalElementos": 3,
  "totalPaginas": 1
}
```

Orden: de la entrada más reciente a la más antigua. Los valores de `curp`, `rfc` y
`telefono` dentro de `cambios` siempre aparecen enmascarados (FR-007).

**Errores**: `403 ACCESO_DENEGADO` (rol distinto de ADMIN), `404 PERSONA_NO_ENCONTRADA`
(el `id` no corresponde a ninguna persona, exista o no actualmente activa).

## PATCH /api/personas/{id}/restaurar

Restaura una persona eliminada lógicamente (US3), solo ADMIN.

**200 OK** → cuerpo: la persona restaurada (mismo shape que `GET /api/personas/{id}`,
incluidos sus campos de auditoría; `modificadoPor`/`modificadoEn` reflejan esta
restauración).

**Errores**:
- `403 ACCESO_DENEGADO` — rol distinto de ADMIN (FR-013).
- `404 PERSONA_NO_ENCONTRADA` — el `id` no existe.
- `409 PERSONA_YA_ACTIVA` — la persona ya está activa (no es una operación válida sobre
  un registro que no fue eliminado — FR-015).
- `409 PERSONA_CORREO_DUPLICADO` / `409 PERSONA_CURP_DUPLICADO` — el correo o CURP de la
  persona a restaurar ya pertenece a otra persona actualmente activa (FR-014).

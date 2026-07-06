# Contrato: Importación y Bitácora del Catálogo SEPOMEX

Extiende `specs/001-personas-codigos-postales/contracts/codigos-postales-api.md`. Todas
las rutas requieren `Authorization: Bearer <accessToken>` de un usuario con rol ADMIN
(`002`). Errores en el formato de
`specs/001-personas-codigos-postales/contracts/error-format.md`.

## POST /api/codigos-postales/importaciones

Sube un archivo de catálogo y ejecuta su importación de inmediato (US2, FR-004).

**Request**: `multipart/form-data`, campo `archivo` con el `.csv`/`.txt` del catálogo.

**200 OK** → cuerpo: resumen de la corrida.

```json
{
  "insertados": 120,
  "actualizados": 8,
  "sinCambio": 149872,
  "rechazados": 3,
  "detallesRechazados": [
    "Línea 4021: código postal 'ABCDE' no son 5 dígitos"
  ]
}
```

**Errores**:
- `400 CATALOGO_ARCHIVO_DEMASIADO_GRANDE` — excede el tamaño máximo configurado.
- `400 CATALOGO_ARCHIVO_INVALIDO` — el archivo no tiene la estructura esperada
  (encabezado o número de columnas); el catálogo no se modifica (FR-011).
- `403 ACCESO_DENEGADO` — rol distinto de ADMIN (FR-005).
- `409 CATALOGO_IMPORTACION_EN_CURSO` — ya hay una importación en curso (programada o
  manual); esta solicitud se rechaza de inmediato, sin encolarse (FR-010).

## GET /api/codigos-postales/importaciones

Bitácora paginada de corridas de importación (US3, FR-007, FR-008).

**200 OK**:

```json
{
  "contenido": [
    {
      "fecha": "string (ISO-8601)",
      "origen": "PROGRAMADA | MANUAL",
      "usuario": "string (login) | null",
      "archivo": "string",
      "duracionMs": 4213,
      "insertados": 120,
      "actualizados": 8,
      "sinCambio": 149872,
      "rechazados": 3,
      "estado": "EXITO | ERROR | RECHAZADA_CONCURRENCIA",
      "detalleError": "string | null"
    }
  ],
  "pagina": 0,
  "tamanoPagina": 20,
  "totalElementos": 12,
  "totalPaginas": 1
}
```

Orden: de la corrida más reciente a la más antigua. `usuario` es `null` cuando
`origen = PROGRAMADA`.

**Errores**: `403 ACCESO_DENEGADO` — rol distinto de ADMIN.

## Efecto sobre los endpoints ya existentes

`GET /api/codigos-postales/{cp}` y `GET /api/colonias` (`specs/001`) no cambian de
schema ni de comportamiento; siguen respondiendo con normalidad durante una
importación en curso (FR-013), sirviendo desde caché hasta que una importación exitosa
la invalida (FR-014).

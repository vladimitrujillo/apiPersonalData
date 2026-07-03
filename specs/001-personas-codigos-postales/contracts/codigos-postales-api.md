# Contrato: API de Catálogo de Códigos Postales (SEPOMEX)

Todos los endpoints requieren el encabezado `X-API-Key` (FR-023) y devuelven errores en el
formato de `error-format.md`. Las respuestas incluyen encabezados HTTP de caché
(`Cache-Control: max-age=...`, research.md §7) dado que el catálogo cambia con poca
frecuencia (FR-018).

## GET /api/codigos-postales/{codigoPostal}

Consulta un código postal exacto (US3, FR-013).

**Path param**: `codigoPostal` — MUST ser exactamente 5 dígitos numéricos.

**200 OK**:

```json
{
  "codigoPostal": "06600",
  "estado": "Ciudad de México",
  "municipio": "Cuauhtémoc",
  "colonias": [
    { "nombre": "Juárez", "tipoAsentamiento": "Colonia" },
    { "nombre": "Roma Norte", "tipoAsentamiento": "Colonia" }
  ]
}
```

**Errores**: `400 CP_FORMATO_INVALIDO` (no son 5 dígitos numéricos, FR-014),
`404 CP_NO_ENCONTRADO` (no existe en el catálogo, FR-015).

## GET /api/colonias

Busca colonias por coincidencia parcial de nombre, opcionalmente acotado (US4, FR-016).

**Query params**:

| Parámetro | Tipo | Requerido | Notas |
|---|---|---|---|
| `nombre` | string | Sí | Fragmento de nombre a buscar (coincidencia parcial) |
| `estado` | string | No | Acota la búsqueda a un estado |
| `municipio` | string | No | Acota la búsqueda a un municipio |

**200 OK**:

```json
[
  {
    "codigoPostal": "06700",
    "estado": "Ciudad de México",
    "municipio": "Cuauhtémoc",
    "nombre": "Roma Sur",
    "tipoAsentamiento": "Colonia"
  }
]
```

Sin coincidencias → `200 OK` con lista vacía `[]` (spec Acceptance Scenario 3, US4), nunca
error.

**Errores**: `400 VALIDACION_FALLIDA` si `nombre` está ausente o vacío.

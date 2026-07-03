# Contrato: API de Personas

Todos los endpoints requieren el encabezado `X-API-Key` (FR-023; ver `research.md` §6) y
devuelven errores en el formato de `error-format.md`. Los campos de tipo fecha usan
`YYYY-MM-DD`.

## POST /api/personas

Crea una persona (US1, FR-001).

**Request body**:

```json
{
  "nombres": "string",
  "apellidos": "string",
  "fechaNacimiento": "YYYY-MM-DD",
  "sexo": "string",
  "curp": "string (18)",
  "rfc": "string (13)",
  "correo": "string",
  "telefono": "string (10 dígitos)",
  "direccion": {
    "calle": "string",
    "numero": "string",
    "colonia": "string",
    "municipio": "string | omitible si se autocompleta por CP",
    "estado": "string | omitible si se autocompleta por CP",
    "codigoPostal": "string (5 dígitos)",
    "pais": "string"
  }
}
```

**201 Created** → cuerpo: Persona creada (incluye `id`, y `direccion.municipio`/`estado`
autocompletados si `pais = MX` y el CP es válido — FR-020).

**Errores**: `400 VALIDACION_FALLIDA`, `400 CP_FORMATO_INVALIDO`, `400 FECHA_NACIMIENTO_FUTURA`,
`400 COLONIA_NO_VALIDA_PARA_CP`, `404 CP_NO_ENCONTRADO` (CP mexicano inexistente, FR-019),
`409 PERSONA_CORREO_DUPLICADO`, `409 PERSONA_CURP_DUPLICADO`.

## GET /api/personas/{id}

Consulta una persona activa por ID (US1, FR-002).

**200 OK** → cuerpo: Persona completa.

**Errores**: `404 PERSONA_NO_ENCONTRADA` (no existe o eliminada lógicamente, FR-012).

## GET /api/personas

Lista personas activas con paginación y filtros (US2, FR-003).

**Query params**:

| Parámetro | Tipo | Requerido | Notas |
|---|---|---|---|
| `page` | int | No (default 0) | Página, base 0 |
| `size` | int | No (default 20, máx 100) | Tamaño de página (Assumptions) |
| `nombre` | string | No | Coincidencia parcial contra nombres+apellidos |
| `municipio` | string | No | Coincidencia exacta o parcial (definir en tasks/implementación) |
| `estado` | string | No | Coincidencia exacta o parcial |

**200 OK**:

```json
{
  "contenido": [ { "...persona...": "..." } ],
  "pagina": 0,
  "tamanoPagina": 20,
  "totalElementos": 42,
  "totalPaginas": 3
}
```

Una página fuera de rango regresa `contenido: []` sin error (spec Acceptance Scenario 5,
US2).

## PATCH /api/personas/{id}

Actualiza parcialmente una persona activa (US1, FR-004).

**Request body**: cualquier subconjunto de los campos de creación (incluida `direccion`
parcial). Los campos no enviados no se modifican.

**200 OK** → cuerpo: Persona actualizada.

**Errores**: mismos que POST (incluyendo duplicados si se cambia `correo`/`curp`), más
`404 PERSONA_NO_ENCONTRADA` si la persona no existe o está eliminada lógicamente
(edge case US1).

## DELETE /api/personas/{id}

Elimina lógicamente una persona (US1, FR-005).

**204 No Content** en éxito.

**Errores**: `404 PERSONA_NO_ENCONTRADA` si no existe o ya fue eliminada previamente
(edge case: eliminar dos veces se trata como no encontrada — Assumptions).

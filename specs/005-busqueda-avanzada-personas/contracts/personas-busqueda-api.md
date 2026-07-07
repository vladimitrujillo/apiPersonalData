# Contrato: `GET /api/personas` (extendido)

Mismo path, método, autenticación (`ADMIN` o `CAPTURISTA`) y formato de respuesta que
hoy. Todos los parámetros de esta tabla son opcionales; los ya existentes (`nombre`,
`municipio`, `estado`, `page`, `size`) no cambian de nombre ni de semántica salvo lo
indicado.

## Query params

| Parámetro | Tipo | Nota |
|---|---|---|
| `nombre` | string | existente; ahora insensible a acentos además de mayúsculas |
| `municipio` | string | existente, sin cambio |
| `estado` | string | existente, sin cambio (geográfico — **no** confundir con `estadoRegistro`) |
| `curpPrefijo` | string | nuevo; coincidencia desde el inicio del CURP |
| `edadMinima` | int >= 0 | nuevo |
| `edadMaxima` | int >= 0 | nuevo |
| `fechaRegistroDesde` | date (`YYYY-MM-DD`) | nuevo |
| `fechaRegistroHasta` | date (`YYYY-MM-DD`) | nuevo |
| `sexo` | string | nuevo; coincidencia exacta |
| `estadoRegistro` | `ACTIVAS`\|`ELIMINADAS`\|`TODAS` | nuevo; sin efecto para CAPTURISTA (forzado a `ACTIVAS`) |
| `ordenarPor` | `NOMBRE`\|`FECHA_NACIMIENTO`\|`FECHA_REGISTRO` | nuevo |
| `direccionOrden` | `ASC`\|`DESC` | nuevo; default `ASC` si se envía `ordenarPor` sin dirección |
| `page` | int | existente, sin cambio |
| `size` | int | existente, sin cambio |

## Respuestas

- **200**: `PersonaPageResponseDTO` — idéntico shape al actual (sin criterios nuevos:
  respuesta byte-a-byte idéntica a hoy, FR-013).
- **400** (`VALIDACION_FALLIDA`): cualquiera de:
  - `edadMinima`/`edadMaxima` negativo (Bean Validation, `campo` = el que corresponda)
  - `edadMinima > edadMaxima` (`campo`: `edadMaxima`)
  - `fechaRegistroDesde > fechaRegistroHasta` (`campo`: `fechaRegistroHasta`)
  - `ordenarPor` fuera de la whitelist (`campo`: `ordenarPor`)
  - `direccionOrden` fuera de la whitelist (`campo`: `direccionOrden`)
- **403**: rol no autenticado con `ADMIN`/`CAPTURISTA` (sin cambio respecto a hoy).

## Ejemplo — combinación de criterios (US2)

```
GET /api/personas?nombre=garcia&edadMinima=18&edadMaxima=40&estado=Jalisco&ordenarPor=FECHA_NACIMIENTO&direccionOrden=DESC
```

→ intersección: nombre/apellido contiene "garcia" (sin acentos) **Y** edad entre 18 y 40
**Y** dirección vigente en estado "Jalisco", ordenado por fecha de nacimiento
descendente.

## Ejemplo — CAPTURISTA intentando ver eliminadas (US3)

```
GET /api/personas?estadoRegistro=ELIMINADAS
Authorization: Bearer <token-CAPTURISTA>
```

→ 200, mismo resultado que sin el parámetro (solo activas) — el parámetro se ignora
silenciosamente para este rol, sin 400 ni 403 por ese motivo.

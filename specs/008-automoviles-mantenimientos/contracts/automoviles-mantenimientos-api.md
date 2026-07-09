# Contrato: Automóviles de Personas y Mantenimientos

Extiende `specs/001-personas-codigos-postales/contracts/error-format.md`.
Todas las rutas requieren `Authorization: Bearer <accessToken>` (`002`).

## Nuevos códigos de error

| HTTP Status | `codigo` | Cuándo se produce |
|---|---|---|
| 409 | `AUTOMOVIL_PLACAS_DUPLICADAS` | Ya existe un automóvil **activo** con esas placas |
| 409 | `AUTOMOVIL_VIN_DUPLICADO` | Ya existe cualquier automóvil (activo o eliminado) con ese VIN |
| 404 | `AUTOMOVIL_NO_ENCONTRADO` | El automóvil referenciado no existe |
| 409 | `AUTOMOVIL_ELIMINADO` | Se intentó registrar/editar un mantenimiento sobre un automóvil eliminado lógicamente |
| 409 | `AUTOMOVIL_YA_ACTIVO` | Se intentó restaurar un automóvil que ya está activo |
| 404 | `MANTENIMIENTO_NO_ENCONTRADO` | El mantenimiento referenciado no existe |
| 409 | `MANTENIMIENTO_YA_ACTIVO` | Se intentó restaurar un mantenimiento que ya está activo |
| 409 | `KILOMETRAJE_INCONSISTENTE` | El kilometraje es menor al del mantenimiento activo más reciente del automóvil |
| 400 | `MECANICO_NO_ENCONTRADO` | El `mecanicoId` no corresponde a ninguna persona registrada |
| 409 | `MECANICO_ELIMINADO` | El `mecanicoId` corresponde a una persona eliminada lógicamente |
| 409 | `MECANICO_SIN_PROFESION_ACTIVA` | El `mecanicoId` corresponde a una persona activa sin la profesión "Mecánico" asignada de forma activa |
| 409 | `PERSONA_ELIMINADA` | (reutilizado de 007) Se intentó registrar un automóvil o un mantenimiento para una persona eliminada lógicamente |

## Automóviles

### POST /api/personas/{id}/automoviles

Registra un automóvil nuevo asociado a una persona activa. ADMIN y CAPTURISTA.

**Request**:

```json
{ "marca": "Nissan", "modelo": "Versa", "anio": 2022, "color": "Rojo", "placas": "ABC-123-A", "vin": "1HGCM82633A004352" }
```

`color` y `vin` son opcionales.

**201 Created** → `AutomovilResponseDTO`:

```json
{
  "id": "uuid", "personaId": "uuid-de-persona",
  "marca": "Nissan", "modelo": "Versa", "anio": 2022, "color": "Rojo",
  "placas": "ABC-123-A", "vin": "1HGCM82633A004352", "activo": true
}
```

**Errores**: `400 VALIDACION_FALLIDA` (campos requeridos vacíos, año fuera de
rango), `404 PERSONA_NO_ENCONTRADA`, `409 PERSONA_ELIMINADA`,
`409 AUTOMOVIL_PLACAS_DUPLICADAS`, `409 AUTOMOVIL_VIN_DUPLICADO`.

### GET /api/personas/{id}/automoviles

Lista los automóviles de una persona (sin paginar). ADMIN y CAPTURISTA.

**200 OK** → lista de `AutomovilResponseDTO`.

**Errores**: `404 PERSONA_NO_ENCONTRADA`.

### GET /api/automoviles/{id}

Consulta el detalle de un automóvil. ADMIN y CAPTURISTA.

**200 OK** → `AutomovilResponseDTO`.

**Errores**: `404 AUTOMOVIL_NO_ENCONTRADO`.

### PATCH /api/automoviles/{id}

Edita marca, modelo, año, color y/o placas de un automóvil activo. El VIN no
se puede modificar (si se envía, se ignora o se rechaza como campo no
editable — ver `tasks.md` para el detalle de implementación). ADMIN y
CAPTURISTA.

**Request**: `{ "marca": "...", "modelo": "...", "anio": 2023, "color": "...", "placas": "..." }` (todos opcionales, solo los enviados se actualizan)

**200 OK** → `AutomovilResponseDTO`.

**Errores**: `400 VALIDACION_FALLIDA`, `404 AUTOMOVIL_NO_ENCONTRADO`,
`409 AUTOMOVIL_PLACAS_DUPLICADAS`.

### DELETE /api/automoviles/{id}

Elimina lógicamente un automóvil activo (oculta también su historial de
mantenimientos). Solo ADMIN.

**204 No Content**.

**Errores**: `403 ACCESO_DENEGADO`, `404 AUTOMOVIL_NO_ENCONTRADO`.

### POST /api/automoviles/{id}/restaurar

Restaura un automóvil eliminado lógicamente, junto con su historial de
mantenimientos. Solo ADMIN.

**200 OK** → `AutomovilResponseDTO`.

**Errores**: `403 ACCESO_DENEGADO`, `404 AUTOMOVIL_NO_ENCONTRADO`,
`409 AUTOMOVIL_YA_ACTIVO`.

## Mantenimientos

### POST /api/automoviles/{id}/mantenimientos

Registra un mantenimiento nuevo con sus piezas cambiadas, en una sola
operación. ADMIN y CAPTURISTA.

**Request**:

```json
{
  "descripcion": "Cambio de balatas y afinación",
  "fecha": "2026-06-01",
  "kilometraje": 45000,
  "costoTotal": 1850.00,
  "mecanicoId": "uuid-de-persona-mecanico",
  "piezas": [
    { "nombre": "Balatas delanteras", "numeroParte": "BD-4471", "costo": 650.00 },
    { "nombre": "Filtro de aceite", "costo": 120.00 }
  ]
}
```

`mecanicoId` y `piezas` son opcionales (`piezas` puede ser `[]` u omitirse).

**201 Created** → `MantenimientoResponseDTO`:

```json
{
  "id": "uuid", "automovilId": "uuid-de-automovil",
  "descripcion": "Cambio de balatas y afinación", "fecha": "2026-06-01",
  "kilometraje": 45000, "costoTotal": 1850.00,
  "mecanico": { "id": "uuid-de-persona-mecanico", "nombreCompleto": "Juan Pérez" },
  "piezas": [
    { "id": "uuid", "nombre": "Balatas delanteras", "numeroParte": "BD-4471", "costo": 650.00 },
    { "id": "uuid", "nombre": "Filtro de aceite", "numeroParte": null, "costo": 120.00 }
  ],
  "activo": true
}
```

`mecanico` es `null` si no se proporcionó `mecanicoId`.

**Errores**: `400 VALIDACION_FALLIDA` (fecha futura, costo o kilometraje
negativo, campos requeridos), `400 MECANICO_NO_ENCONTRADO`,
`404 AUTOMOVIL_NO_ENCONTRADO`, `409 AUTOMOVIL_ELIMINADO`,
`409 PERSONA_ELIMINADA` (persona dueña del automóvil), `409 MECANICO_ELIMINADO`,
`409 MECANICO_SIN_PROFESION_ACTIVA`, `409 KILOMETRAJE_INCONSISTENTE`.

### GET /api/automoviles/{id}/mantenimientos

Historial de mantenimientos de un automóvil, paginado, ordenado por fecha
descendente. ADMIN y CAPTURISTA.

**Query params**: `page` (base 0), `size` (máx. 100, por defecto 20).

**200 OK**:

```json
{
  "contenido": [ /* MantenimientoResponseDTO[] */ ],
  "pagina": 0, "tamanoPagina": 20, "totalElementos": 3, "totalPaginas": 1
}
```

**Errores**: `404 AUTOMOVIL_NO_ENCONTRADO`.

### GET /api/mantenimientos/{id}

Detalle de un mantenimiento, con sus piezas y los datos básicos del
mecánico. ADMIN y CAPTURISTA.

**200 OK** → `MantenimientoResponseDTO`.

**Errores**: `404 MANTENIMIENTO_NO_ENCONTRADO`.

### PATCH /api/mantenimientos/{id}

Actualiza un mantenimiento existente, revalidando las mismas reglas que al
registrarlo. Si se envía `piezas`, reemplaza la colección completa. ADMIN y
CAPTURISTA.

**Request**: mismos campos que el alta, todos opcionales (solo los enviados
se actualizan; `piezas`, si se envía, siempre reemplaza el conjunto
completo).

**200 OK** → `MantenimientoResponseDTO`.

**Errores**: mismos que el alta, más `404 MANTENIMIENTO_NO_ENCONTRADO`.

### DELETE /api/mantenimientos/{id}

Elimina lógicamente un mantenimiento (no afecta al automóvil ni a los demás
mantenimientos). Solo ADMIN.

**204 No Content**.

**Errores**: `403 ACCESO_DENEGADO`, `404 MANTENIMIENTO_NO_ENCONTRADO`.

### POST /api/mantenimientos/{id}/restaurar

Restaura un mantenimiento eliminado lógicamente. Solo ADMIN.

**200 OK** → `MantenimientoResponseDTO`.

**Errores**: `403 ACCESO_DENEGADO`, `404 MANTENIMIENTO_NO_ENCONTRADO`,
`409 MANTENIMIENTO_YA_ACTIVO`.

## Efecto sobre los endpoints ya existentes

`GET/POST/PATCH/DELETE /api/personas/**` y `**/profesiones/**` (specs `001`,
`003`, `004`, `007`) no cambian de schema ni de comportamiento. El borrado
lógico y la restauración de una persona ahora también afectan la capacidad
de registrar automóviles/mantenimientos nuevos para ella (FR-006/FR-013),
sin modificar sus automóviles ya existentes.

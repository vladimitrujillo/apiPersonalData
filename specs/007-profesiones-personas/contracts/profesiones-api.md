# Contrato: Catálogo de Profesiones y Asignación a Personas

Extiende `specs/001-personas-codigos-postales/contracts/error-format.md`.
Todas las rutas requieren `Authorization: Bearer <accessToken>` (`002`).

## Nuevos códigos de error

| HTTP Status | `codigo` | Cuándo se produce |
|---|---|---|
| 409 | `PROFESION_NOMBRE_DUPLICADO` | Ya existe una profesión **activa** con ese nombre (comparación insensible a mayúsculas/acentos) |
| 409 | `PROFESION_NOMBRE_DESACTIVADA` | Ya existe una profesión **desactivada** con ese nombre; incluye el `id` de esa profesión para que un ADMIN pueda reactivarla |
| 409 | `PROFESION_DESACTIVADA` | Se intentó asignar una profesión que está desactivada en el catálogo |
| 404 | `PROFESION_NO_ENCONTRADA` | La profesión referenciada no existe en el catálogo |
| 409 | `PERSONA_PROFESION_YA_ASIGNADA` | La persona ya tiene esa profesión asignada de forma activa |
| 409 | `PERSONA_ELIMINADA` | Se intentó asignar una profesión a una persona eliminada lógicamente |
| 404 | `PERSONA_PROFESION_NO_ENCONTRADA` | La asignación referenciada (para retirar o consultar) no existe |
| 409 | `PERSONA_PROFESION_YA_RETIRADA` | Se intentó retirar una asignación que ya estaba retirada |

## Catálogo de profesiones

### POST /api/profesiones

Crea una profesión nueva. Solo ADMIN.

**Request**:

```json
{ "nombre": "Electricista", "descripcion": "string opcional" }
```

**201 Created** → `ProfesionResponseDTO`:

```json
{ "id": 2, "nombre": "Electricista", "descripcion": "string opcional", "activo": true }
```

**Errores**: `400 VALIDACION_FALLIDA` (nombre vacío), `403 ACCESO_DENEGADO`,
`409 PROFESION_NOMBRE_DUPLICADO`, `409 PROFESION_NOMBRE_DESACTIVADA`.

### PATCH /api/profesiones/{id}

Edita la descripción de una profesión existente (el nombre no se puede
cambiar). Solo ADMIN.

**Request**: `{ "descripcion": "string opcional" }`

**200 OK** → `ProfesionResponseDTO`.

**Errores**: `403 ACCESO_DENEGADO`, `404 PROFESION_NO_ENCONTRADA`.

### PATCH /api/profesiones/{id}/desactivar

Desactiva una profesión activa (no la elimina; FR-008). Solo ADMIN.

**200 OK** → `ProfesionResponseDTO` (`activo: false`).

**Errores**: `403 ACCESO_DENEGADO`, `404 PROFESION_NO_ENCONTRADA`.

### PATCH /api/profesiones/{id}/reactivar

Reactiva una profesión desactivada. Solo ADMIN.

**200 OK** → `ProfesionResponseDTO` (`activo: true`).

**Errores**: `403 ACCESO_DENEGADO`, `404 PROFESION_NO_ENCONTRADA`.

### GET /api/profesiones

Lista el catálogo, paginado. Por defecto solo profesiones activas. ADMIN y
CAPTURISTA.

**Query params**: `page` (base 0), `size` (máx. 100, por defecto 20),
`incluirInactivas` (boolean, por defecto `false`; solo tiene efecto para
ADMIN — un CAPTURISTA que lo envíe sigue viendo solo activas).

**200 OK** → página de `ProfesionResponseDTO`.

## Asignación de profesiones a una persona

### POST /api/personas/{id}/profesiones

Asigna una profesión del catálogo a una persona. ADMIN y CAPTURISTA.

**Request**:

```json
{ "profesionId": 1, "fechaDesde": "2026-01-15", "cedula": "string opcional" }
```

`fechaDesde` es opcional (por defecto, hoy).

**201 Created** → `AsignacionProfesionResponseDTO`:

```json
{
  "id": "uuid",
  "profesionId": 1,
  "profesionNombre": "Mecánico",
  "fechaDesde": "2026-01-15",
  "cedula": "string opcional",
  "activo": true
}
```

**Errores**: `400 VALIDACION_FALLIDA`, `403 ACCESO_DENEGADO`,
`404 PERSONA_NO_ENCONTRADA`, `404 PROFESION_NO_ENCONTRADA`,
`409 PERSONA_ELIMINADA`, `409 PROFESION_DESACTIVADA`,
`409 PERSONA_PROFESION_YA_ASIGNADA`.

### GET /api/personas/{id}/profesiones

Lista las profesiones asignadas a una persona. Por defecto solo activas;
`incluirRetiradas=true` (solo ADMIN) también incluye el histórico retirado.
ADMIN y CAPTURISTA.

**200 OK** → lista de `AsignacionProfesionResponseDTO`.

**Errores**: `403 ACCESO_DENEGADO`, `404 PERSONA_NO_ENCONTRADA`.

### PATCH /api/personas/{id}/profesiones/{asignacionId}/retirar

Retira (desactiva) una asignación activa; no la elimina. ADMIN y
CAPTURISTA.

**200 OK** → `AsignacionProfesionResponseDTO` (`activo: false`).

**Errores**: `403 ACCESO_DENEGADO`, `404 PERSONA_NO_ENCONTRADA`,
`404 PERSONA_PROFESION_NO_ENCONTRADA`, `409 PERSONA_PROFESION_YA_RETIRADA`.

## Directorio por profesión

### GET /api/profesiones/{id}/personas

Directorio paginado de personas activas con una asignación activa de esta
profesión. ADMIN y CAPTURISTA.

**Query params**: `page` (base 0), `size` (máx. 100, por defecto 20).

**200 OK**:

```json
{
  "contenido": [
    {
      "id": "uuid-de-persona",
      "nombreCompleto": "Juana Pérez",
      "fechaDesde": "2026-01-15",
      "cedula": "string opcional"
    }
  ],
  "pagina": 0,
  "tamanoPagina": 20,
  "totalElementos": 1,
  "totalPaginas": 1
}
```

No incluye ningún otro dato personal (sin correo, teléfono, CURP, RFC,
dirección — FR-018).

**Errores**: `403 ACCESO_DENEGADO`, `404 PROFESION_NO_ENCONTRADA`.

## Efecto sobre los endpoints ya existentes

`GET/POST/PATCH/DELETE /api/personas/**` (specs `001`, `003`, `004`) no
cambian de schema ni de comportamiento. El borrado lógico y la restauración
de una persona (`DELETE /api/personas/{id}`, `POST
/api/personas/{id}/restaurar`) ahora también afectan su visibilidad en los
directorios de profesión (FR-019), sin modificar sus asignaciones (FR-006 de
`004`/Edge Cases de `007`).

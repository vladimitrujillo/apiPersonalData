# Contrato: Formato de Error (Principio V de la constitución)

Todas las respuestas de error de la API (módulo Personas, módulo Códigos Postales, módulo
Autenticación y módulo Usuarios — ver `specs/002-autenticacion-autorizacion/contracts/`
— y módulo de Auditoría/Historial — ver
`specs/003-auditoria-personas/contracts/personas-historial-api.md`) usan el mismo cuerpo
JSON, producido por un `@RestControllerAdvice` centralizado.

## Esquema

```json
{
  "codigo": "string",
  "mensaje": "string",
  "detalles": [
    { "campo": "string", "motivo": "string" }
  ]
}
```

- `codigo`: identificador estable y programático del error (ver catálogo abajo). Siempre
  presente.
- `mensaje`: descripción legible por humanos del error general. Siempre presente.
- `detalles`: lista opcional de errores por campo (validación de entrada). Ausente u
  omitida (`[]`) cuando el error no es de validación de campos.

## Catálogo de códigos de error

| HTTP Status | `codigo` | Cuándo se produce |
|---|---|---|
| 400 | `VALIDACION_FALLIDA` | Uno o más campos no cumplen las reglas de formato/obligatoriedad (Bean Validation) — incluye `detalles` |
| 400 | `CP_FORMATO_INVALIDO` | El código postal enviado no son exactamente 5 dígitos numéricos (FR-014) |
| 400 | `FECHA_NACIMIENTO_FUTURA` | La fecha de nacimiento es posterior a hoy (FR-008) |
| 400 | `COLONIA_NO_VALIDA_PARA_CP` | La colonia enviada no pertenece a la lista de colonias del CP dado (FR-021) |
| 404 | `PERSONA_NO_ENCONTRADA` | La persona no existe o está eliminada lógicamente (FR-012) |
| 404 | `CP_NO_ENCONTRADO` | El código postal consultado o referenciado no existe en el catálogo (FR-015, FR-019) |
| 409 | `PERSONA_CORREO_DUPLICADO` | El correo ya está en uso por otra persona activa (FR-006) |
| 409 | `PERSONA_CURP_DUPLICADO` | El CURP ya está en uso por otra persona activa (FR-007) |
| 401 | `NO_AUTENTICADO` | Falta el token de acceso, es inválido/expiró, el login falló, o el token de refresco es inválido/expirado/ya usado/de un usuario desactivado (specs/002-autenticacion-autorizacion, FR-002, FR-003, FR-005) |
| 403 | `ACCESO_DENEGADO` | El token es válido pero el rol del usuario no autoriza la operación (specs/002-autenticacion-autorizacion, FR-008, FR-010, FR-019) |
| 409 | `USUARIO_LOGIN_DUPLICADO` | El login ya pertenece a otro usuario del sistema, esté activo o desactivado (specs/002-autenticacion-autorizacion, FR-012) |
| 409 | `PERSONA_YA_ACTIVA` | Se intentó restaurar una persona que ya está activa (specs/003-auditoria-personas, FR-015; specs/004-restaurar-persona-curp, FR-007) |
| 409 | `PERSONA_CURP_ELIMINADA` | La CURP enviada al crear/actualizar pertenece a un registro eliminado lógicamente; incluye el `id` de ese registro para que un ADMIN pueda restaurarlo (specs/004-restaurar-persona-curp, FR-004) |
| 400 | `CATALOGO_ARCHIVO_INVALIDO` | El archivo de catálogo SEPOMEX subido no tiene la estructura esperada (encabezado o columnas); el catálogo no se modifica (specs/006-sepomex-import-automatico, FR-011) |
| 400 | `CATALOGO_ARCHIVO_DEMASIADO_GRANDE` | El archivo de catálogo subido excede el tamaño máximo configurado (specs/006-sepomex-import-automatico) |
| 409 | `CATALOGO_IMPORTACION_EN_CURSO` | Ya hay una importación del catálogo en curso; la nueva solicitud se rechaza de inmediato (specs/006-sepomex-import-automatico, FR-010) |

## Ejemplo — error de validación con múltiples campos

```json
{
  "codigo": "VALIDACION_FALLIDA",
  "mensaje": "La solicitud contiene uno o más campos inválidos",
  "detalles": [
    { "campo": "correo", "motivo": "El formato de correo electrónico no es válido" },
    { "campo": "telefono", "motivo": "El teléfono debe tener exactamente 10 dígitos numéricos" }
  ]
}
```

## Ejemplo — error de duplicado

```json
{
  "codigo": "PERSONA_CORREO_DUPLICADO",
  "mensaje": "Ya existe una persona activa registrada con este correo electrónico",
  "detalles": [
    { "campo": "correo", "motivo": "Debe ser único entre personas activas" }
  ]
}
```

## Ejemplo — recurso no encontrado

```json
{
  "codigo": "CP_NO_ENCONTRADO",
  "mensaje": "No existe un código postal '00000' en el catálogo",
  "detalles": []
}
```

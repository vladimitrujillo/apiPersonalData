# Contrato: Formato de Error (Principio V de la constitución)

Todas las respuestas de error de la API (módulo Personas y módulo Códigos Postales) usan
el mismo cuerpo JSON, producido por un `@RestControllerAdvice` centralizado.

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
| 401 | `NO_AUTENTICADO` | Falta la clave de API o es inválida (FR-023) |

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

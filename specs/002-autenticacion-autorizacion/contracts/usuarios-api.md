# Contrato: API de Gestión de Usuarios del Sistema

Todas las rutas requieren `Authorization: Bearer <accessToken>` de un usuario con rol
**ADMIN** (US2, US4, FR-010). Un token válido de rol CAPTURISTA recibe
`403 ACCESO_DENEGADO` en cualquiera de estas rutas (FR-008). Errores en el formato de
`specs/001-personas-codigos-postales/contracts/error-format.md`.

## POST /api/usuarios

Crea un usuario del sistema (US4, FR-001 implícito de alta).

**Request body**:

```json
{
  "login": "string",
  "password": "string",
  "nombre": "string",
  "rol": "ADMIN | CAPTURISTA"
}
```

**201 Created** → cuerpo: usuario creado (sin contraseña ni hash — FR-016).

```json
{
  "id": "uuid",
  "login": "string",
  "nombre": "string",
  "rol": "ADMIN | CAPTURISTA",
  "activo": true
}
```

**Errores**: `400 VALIDACION_FALLIDA`, `409 USUARIO_LOGIN_DUPLICADO` (el login ya
pertenece a otro usuario, esté activo o desactivado — FR-012).

## GET /api/usuarios

Lista los usuarios del sistema (US4).

**200 OK** → cuerpo: lista de usuarios (mismo shape que la respuesta de creación, sin
contraseña ni hash — Acceptance Scenario US4 #2).

## PATCH /api/usuarios/{id}/desactivar

Desactiva un usuario (US4, FR-013).

**200 OK** → cuerpo: usuario actualizado (`activo: false`). A partir de esta llamada, el
usuario no puede autenticarse (login → 401) y cualquier `refreshToken` suyo deja de ser
válido en su próximo uso (FR-014).

**Errores**: `404` (no existe un usuario con ese `id`, mismo estilo que
`PERSONA_NO_ENCONTRADA` para el módulo de usuarios).

## PATCH /api/usuarios/{id}/contrasena

Restablece la contraseña de un usuario (US4, FR-021 — el ADMIN provee la nueva
contraseña directamente, decisión tomada en `/speckit-specify`).

**Request body**:

```json
{
  "nuevaContrasena": "string"
}
```

**204 No Content** en éxito. La contraseña anterior queda invalidada de inmediato.

**Errores**: `400 VALIDACION_FALLIDA` (no cumple la política mínima de contraseña),
`404` (no existe un usuario con ese `id`).

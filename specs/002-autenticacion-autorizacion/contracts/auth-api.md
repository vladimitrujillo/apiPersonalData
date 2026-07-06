# Contrato: API de Autenticación

Rutas públicas (no requieren token — spec FR-018). Errores en el formato de
`specs/001-personas-codigos-postales/contracts/error-format.md`.

## POST /login

Inicia sesión con usuario y contraseña (US1, FR-001).

**Request body**:

```json
{
  "login": "string",
  "password": "string"
}
```

**200 OK** → cuerpo:

```json
{
  "accessToken": "string (JWT)",
  "refreshToken": "string (opaco)",
  "expiraEn": "string (ISO-8601, expiración del accessToken)"
}
```

**Errores**: `401 NO_AUTENTICADO` — credenciales incorrectas, usuario inexistente, o
usuario desactivado (FR-003, FR-013). El mensaje es genérico en los tres casos; no
distingue cuál ocurrió.

## POST /refresh

Renueva el token de acceso usando un token de refresco vigente (US3, FR-004). Ruta
pública: no requiere `Authorization`, el propio `refreshToken` es la credencial.

**Request body**:

```json
{
  "refreshToken": "string"
}
```

**200 OK** → mismo cuerpo que `POST /login` (`accessToken`, `refreshToken` nuevo —
rotación, `expiraEn`). El `refreshToken` recibido queda invalidado tras esta llamada
(no puede reutilizarse — edge case de spec).

**Errores**: `401 NO_AUTENTICADO` — token de refresco inválido, expirado, ya usado
(rotado previamente), o perteneciente a un usuario desactivado (FR-005, FR-014).

## Uso del token de acceso en el resto de la API

Todas las rutas protegidas (personas, códigos postales, gestión de usuarios) requieren
el encabezado:

```text
Authorization: Bearer <accessToken>
```

**Errores comunes a cualquier ruta protegida**:

| HTTP Status | `codigo` | Cuándo se produce |
|---|---|---|
| 401 | `NO_AUTENTICADO` | Sin encabezado `Authorization`, token malformado, firma inválida, o expirado (FR-002) |
| 403 | `ACCESO_DENEGADO` | Token válido, pero el rol del usuario no autoriza la operación (FR-008, FR-010, FR-019) |

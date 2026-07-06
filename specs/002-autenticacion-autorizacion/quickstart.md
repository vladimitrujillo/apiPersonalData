# Quickstart: Validación de Autenticación y Autorización

Guía para validar de extremo a extremo que el feature funciona, una vez implementado.
Referencia: `contracts/auth-api.md` y `contracts/usuarios-api.md` para los esquemas
exactos de request/response, y `data-model.md` para las entidades.

## Prerrequisitos

- Java 21, Maven, Docker (para PostgreSQL local / Testcontainers).
- Variables de entorno configuradas antes del primer arranque:
  - `JWT_SECRET` (llave HS256, sin valor por defecto — research.md §1).
  - `ADMIN_BOOTSTRAP_LOGIN` / `ADMIN_BOOTSTRAP_PASSWORD` (primer ADMIN — research.md §6).
- Migraciones Flyway aplicadas (arranque de la aplicación las ejecuta automáticamente,
  incluida `V2__create_usuario_refresh_token.sql`).
- El `X-API-Key` del feature anterior ya no aplica a ninguna ruta (FR-006a).

## 1. Levantar la aplicación

```bash
export JWT_SECRET="una-llave-secreta-de-al-menos-32-bytes-solo-para-desarrollo-local"
export ADMIN_BOOTSTRAP_LOGIN="admin"
export ADMIN_BOOTSTRAP_PASSWORD="cambia-esto-en-produccion"

docker compose up -d db
./mvnw spring-boot:run
```

**Validar bootstrap idempotente**: detener y volver a arrancar la aplicación; confirmar
que sigue existiendo exactamente un usuario ADMIN (no se duplica):

```bash
psql -h localhost -U app -d personas -c "SELECT login, rol, activo FROM usuario;"
```

## 2. US1 — Acceso autenticado al API existente

```bash
# Sin token: cualquier endpoint existente rechaza con 401
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/personas
# Esperado: 401 NO_AUTENTICADO

# Login con el ADMIN sembrado
LOGIN_RESPONSE=$(curl -s -X POST -H "Content-Type: application/json" \
  -d "{\"login\":\"$ADMIN_BOOTSTRAP_LOGIN\",\"password\":\"$ADMIN_BOOTSTRAP_PASSWORD\"}" \
  http://localhost:8080/login)

ACCESS_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.accessToken')
REFRESH_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.refreshToken')

# Con token: el mismo endpoint ahora responde normalmente
curl -s -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:8080/api/personas | jq

# Login fallido: mismo 401 genérico, exista o no el usuario
curl -s -o /dev/null -w "%{http_code}\n" -X POST -H "Content-Type: application/json" \
  -d '{"login":"no-existe","password":"lo-que-sea"}' http://localhost:8080/login
# Esperado: 401 NO_AUTENTICADO
```

## 3. US4 — Crear un CAPTURISTA (como ADMIN) y US2 — verificar su autorización

```bash
# Alta de un CAPTURISTA
CAPTURISTA_ID=$(curl -s -X POST -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"login":"jperez","password":"clave-temporal-1","nombre":"Juan Pérez","rol":"CAPTURISTA"}' \
  http://localhost:8080/api/usuarios | jq -r '.id')

# Login como CAPTURISTA
CAP_ACCESS_TOKEN=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"login":"jperez","password":"clave-temporal-1"}' \
  http://localhost:8080/login | jq -r '.accessToken')

# Permitido: crear/consultar/listar/actualizar personas, consultar códigos postales
curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $CAP_ACCESS_TOKEN" \
  http://localhost:8080/api/personas
# Esperado: 200

# Prohibido: eliminar una persona
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE \
  -H "Authorization: Bearer $CAP_ACCESS_TOKEN" \
  http://localhost:8080/api/personas/00000000-0000-0000-0000-000000000000
# Esperado: 403 ACCESO_DENEGADO

# Prohibido: gestión de usuarios
curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $CAP_ACCESS_TOKEN" \
  http://localhost:8080/api/usuarios
# Esperado: 403 ACCESO_DENEGADO
```

## 4. US3 — Continuidad de sesión con refresh token (rotación)

```bash
# Renovar con el refresh token obtenido en el paso 2
REFRESH_RESPONSE=$(curl -s -X POST -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}" http://localhost:8080/refresh)

NEW_ACCESS_TOKEN=$(echo "$REFRESH_RESPONSE" | jq -r '.accessToken')
NEW_REFRESH_TOKEN=$(echo "$REFRESH_RESPONSE" | jq -r '.refreshToken')

# El nuevo access token funciona
curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $NEW_ACCESS_TOKEN" \
  http://localhost:8080/api/personas
# Esperado: 200

# El refresh token original ya no sirve (rotación de un solo uso — decisión D2)
curl -s -o /dev/null -w "%{http_code}\n" -X POST -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}" http://localhost:8080/refresh
# Esperado: 401 NO_AUTENTICADO
```

## 5. US4 — Desactivar un usuario y verificar el bloqueo de login/refresh

```bash
curl -s -X PATCH -H "Authorization: Bearer $ACCESS_TOKEN" \
  http://localhost:8080/api/usuarios/$CAPTURISTA_ID/desactivar | jq

# Ya no puede autenticarse
curl -s -o /dev/null -w "%{http_code}\n" -X POST -H "Content-Type: application/json" \
  -d '{"login":"jperez","password":"clave-temporal-1"}' http://localhost:8080/login
# Esperado: 401 NO_AUTENTICADO

# Intentar reutilizar su login para un usuario nuevo falla (FR-012)
curl -s -o /dev/null -w "%{http_code}\n" -X POST -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"login":"jperez","password":"otra-clave","nombre":"Otro","rol":"CAPTURISTA"}' \
  http://localhost:8080/api/usuarios
# Esperado: 409 USUARIO_LOGIN_DUPLICADO
```

## Criterio de éxito del quickstart

- Paso 2 demuestra SC-001 (401 sin token) y el flujo básico de login.
- Paso 3 demuestra SC-002 (CAPTURISTA opera sobre personas, pero recibe 403 en eliminar
  y en gestión de usuarios).
- Paso 4 demuestra SC-003 (continuidad de sesión vía refresh) y la rotación de un solo
  uso del token de refresco.
- Paso 5 demuestra SC-004 (login desactivado jamás reutilizable) y el bloqueo de
  autenticación tras desactivación (FR-013/FR-014).
- La suite de tests automatizados (ajustada, ver `research.md` §7) debe pasar al 100%
  (SC-005).

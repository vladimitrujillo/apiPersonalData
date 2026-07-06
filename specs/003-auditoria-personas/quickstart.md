# Quickstart: Validación de Auditoría y Historial de Cambios en Personas

Guía para validar de extremo a extremo que el feature funciona, una vez implementado
**y una vez que `002-autenticacion-autorizacion` esté también implementado** (ver
`plan.md` § Dependencia bloqueante). Referencia: `contracts/personas-historial-api.md`
para los esquemas exactos, `data-model.md` para las entidades.

## Prerrequisitos

- Java 21, Maven, Docker (para PostgreSQL local / Testcontainers).
- `002` implementado y funcionando: login disponible, al menos un usuario ADMIN y un
  usuario CAPTURISTA existentes (ver `specs/002-autenticacion-autorizacion/quickstart.md`).
- Migraciones Flyway aplicadas, incluida `V3__add_auditoria_personas.sql` (posterior a
  `V2` de `002`).

## 1. Preparar tokens

```bash
ADMIN_TOKEN=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"login":"admin","password":"..."}' http://localhost:8080/login | jq -r '.accessToken')

CAP_TOKEN=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"login":"jperez","password":"..."}' http://localhost:8080/login | jq -r '.accessToken')
```

## 2. US1 — Auditoría básica visible en consulta por ID

```bash
# Crear una persona autenticado como ADMIN
PERSONA_ID=$(curl -s -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{ "...": "mismo body que specs/001.../quickstart.md paso 5" }' \
  http://localhost:8080/api/personas | jq -r '.id')

# Consultar por ID: debe incluir creadoPor/creadoEn
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/personas/$PERSONA_ID | jq '.creadoPor, .creadoEn, .modificadoPor, .modificadoEn'
# Esperado: creadoPor="admin", modificadoPor="admin" (misma operación de creación)

# Modificar autenticado como CAPTURISTA
curl -s -X PATCH -H "Authorization: Bearer $CAP_TOKEN" -H "Content-Type: application/json" \
  -d '{"telefono": "5587654321"}' http://localhost:8080/api/personas/$PERSONA_ID > /dev/null

curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/personas/$PERSONA_ID | jq '.modificadoPor, .modificadoEn'
# Esperado: modificadoPor="jperez" (creadoPor sigue siendo "admin")

# El listado NO incluye estos campos
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/api/personas | \
  jq '.contenido[0] | has("creadoPor")'
# Esperado: false
```

## 3. US2 — Historial completo (solo ADMIN)

```bash
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/personas/$PERSONA_ID/historial | jq
```

**Esperado**: 200 con al menos dos entradas — `CREACION` (autor `admin`) y
`MODIFICACION` (autor `jperez`, `cambios` incluye `{"campo":"telefono", ...}` con el
teléfono enmascarado).

```bash
curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $CAP_TOKEN" \
  http://localhost:8080/api/personas/$PERSONA_ID/historial
# Esperado: 403 ACCESO_DENEGADO
```

## 4. US3 — Eliminar y restaurar

```bash
# Eliminar lógicamente (ADMIN)
curl -s -X DELETE -H "Authorization: Bearer $ADMIN_TOKEN" -o /dev/null -w "%{http_code}\n" \
  http://localhost:8080/api/personas/$PERSONA_ID
# Esperado: 204

# CAPTURISTA no puede restaurar
curl -s -o /dev/null -w "%{http_code}\n" -X PATCH -H "Authorization: Bearer $CAP_TOKEN" \
  http://localhost:8080/api/personas/$PERSONA_ID/restaurar
# Esperado: 403 ACCESO_DENEGADO

# ADMIN restaura
curl -s -X PATCH -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/personas/$PERSONA_ID/restaurar | jq

# Vuelve a aparecer en consulta y listado
curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/personas/$PERSONA_ID
# Esperado: 200

# El historial ahora incluye ELIMINACION y RESTAURACION
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/personas/$PERSONA_ID/historial | jq '.contenido[].operacion'
# Esperado: ["RESTAURACION","ELIMINACION","MODIFICACION","CREACION"] (más reciente primero)
```

## Criterio de éxito del quickstart

- Paso 2 demuestra SC-001/SC-002 (auditoría visible en detalle, ausente en listado).
- Paso 3 demuestra SC-003/SC-004/SC-005 (historial completo, campos sensibles
  enmascarados, CAPTURISTA rechazado).
- Paso 4 demuestra el ciclo eliminar → restaurar y que ambas operaciones quedan en el
  historial (spec Acceptance Scenarios US3).
- SC-006 (rollback si falla el guardado del historial) y SC-007 (suite existente en
  verde) se validan en pruebas automatizadas, no en este quickstart manual.

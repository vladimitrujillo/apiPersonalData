# Quickstart: Validación de Restauración y Unicidad de CURP Global

Guía para validar de extremo a extremo, una vez implementado este feature **y**
`002`/`003` (ver `plan.md` § Dependencias). Referencia:
`contracts/personas-restaurar-api.md`, `data-model.md`.

## Prerrequisitos

- `002` y `003` implementados y funcionando (login, roles, historial).
- Migración `V4__globalizar_unicidad_curp.sql` aplicada sin errores (si falló por
  duplicados preexistentes, resolverlos manualmente antes de continuar — research.md §2).

## 1. Preparar tokens

```bash
ADMIN_TOKEN=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"login":"admin","password":"..."}' http://localhost:8080/login | jq -r '.accessToken')
CAP_TOKEN=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"login":"jperez","password":"..."}' http://localhost:8080/login | jq -r '.accessToken')
```

## 2. US1 — El correo de una persona eliminada se puede reutilizar

```bash
# Crear y eliminar una persona con correo "ana@example.com"
PERSONA_1=$(curl -s -X POST -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{ "correo": "ana@example.com", "curp": "AAAA000101HDFRRN01", "...": "..." }' \
  http://localhost:8080/api/personas | jq -r '.id')

curl -s -X DELETE -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/api/personas/$PERSONA_1

# Registrar una persona nueva con el mismo correo
curl -s -o /dev/null -w "%{http_code}\n" -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{ "correo": "ana@example.com", "curp": "BBBB000101HDFRRN02", "...": "..." }' \
  http://localhost:8080/api/personas
# Esperado: 201 (D3 en acción)
```

## 3. US2 — CURP de un registro eliminado responde 409 accionable

```bash
curl -s -X POST -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{ "curp": "AAAA000101HDFRRN01", "correo": "otro@example.com", "...": "..." }' \
  http://localhost:8080/api/personas | jq
```

**Esperado**: 409 con `codigo: "PERSONA_CURP_ELIMINADA"`, `detalles[0].motivo` incluye el
`id` de `PERSONA_1`, sin nombres/correo/teléfono de esa persona en la respuesta.

## 4. US3 — Restaurar

```bash
# CAPTURISTA no puede restaurar
curl -s -o /dev/null -w "%{http_code}\n" -X POST -H "Authorization: Bearer $CAP_TOKEN" \
  http://localhost:8080/api/personas/$PERSONA_1/restaurar
# Esperado: 403

# ADMIN restaura
curl -s -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/personas/$PERSONA_1/restaurar | jq
# Esperado: 200, datos y dirección intactos

# Restaurar de nuevo (ya activa)
curl -s -o /dev/null -w "%{http_code}\n" -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/personas/$PERSONA_1/restaurar
# Esperado: 409 PERSONA_YA_ACTIVA

# Restaurar un id inexistente
curl -s -o /dev/null -w "%{http_code}\n" -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/personas/00000000-0000-0000-0000-000000000000/restaurar
# Esperado: 404

# Historial incluye ELIMINACION y RESTAURACION
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/personas/$PERSONA_1/historial | jq '.contenido[].operacion'
```

## 5. Restaurar con conflicto de correo

```bash
# Eliminar PERSONA_1 de nuevo, y hacer que otra persona activa tome su correo
curl -s -X DELETE -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/api/personas/$PERSONA_1

curl -s -X POST -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{ "correo": "ana@example.com", "curp": "CCCC000101HDFRRN03", "...": "..." }' \
  http://localhost:8080/api/personas > /dev/null

# Intentar restaurar PERSONA_1 ahora falla por correo, sin alterar nada
curl -s -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/personas/$PERSONA_1/restaurar | jq
# Esperado: 409 PERSONA_CORREO_DUPLICADO, detalles indica el id de la persona activa
```

## 6. US4 — Vista de eliminados

```bash
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/api/personas/eliminadas | jq
# Esperado: solo personas con activo=false

curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $CAP_TOKEN" \
  http://localhost:8080/api/personas/eliminadas
# Esperado: 403
```

## Criterio de éxito del quickstart

- Paso 2 demuestra SC-001. Paso 3 demuestra SC-002/SC-003. Paso 4 demuestra SC-004/SC-005.
  Paso 5 demuestra SC-006. Paso 6 demuestra el acceso restringido de US4.
- SC-007 (suite existente en verde) se valida en CI, no en este quickstart manual.

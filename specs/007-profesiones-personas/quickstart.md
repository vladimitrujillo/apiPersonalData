# Quickstart: Validación del Catálogo de Profesiones y Asignación a Personas

Guía para validar de extremo a extremo, una vez implementado. Referencia:
`contracts/profesiones-api.md`, `data-model.md`.

## Prerrequisitos

- `002-autenticacion-autorizacion` funcionando (login, roles ADMIN/CAPTURISTA).
- Migración `V7__create_profesion_persona_profesion.sql` aplicada (incluye
  la semilla "Mecánico").
- Al menos una persona activa ya registrada (`001`).

## 1. Preparar tokens

```bash
ADMIN_TOKEN=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"login":"admin","password":"..."}' http://localhost:8080/login | jq -r '.accessToken')
CAP_TOKEN=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"login":"capturista","password":"..."}' http://localhost:8080/login | jq -r '.accessToken')
PERSONA_ID="<id-de-una-persona-activa-existente>"
```

## 2. US1 — Administrar el catálogo

```bash
# Crear "Electricista"
curl -s -X POST -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"nombre":"Electricista"}' http://localhost:8080/api/profesiones | jq
# Esperado: 201, activo: true

# Duplicado insensible a acentos contra la semilla "Mecánico"
curl -s -o /dev/null -w "%{http_code}\n" -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" -d '{"nombre":"mecanico"}' http://localhost:8080/api/profesiones
# Esperado: 409 PROFESION_NOMBRE_DUPLICADO

# CAPTURISTA no puede crear
curl -s -o /dev/null -w "%{http_code}\n" -X POST -H "Authorization: Bearer $CAP_TOKEN" \
  -H "Content-Type: application/json" -d '{"nombre":"Plomero"}' http://localhost:8080/api/profesiones
# Esperado: 403
```

## 3. US2 — Asignar profesiones a una persona

```bash
MECANICO_ID=1  # semilla

curl -s -X POST -H "Authorization: Bearer $CAP_TOKEN" -H "Content-Type: application/json" \
  -d "{\"profesionId\":$MECANICO_ID}" \
  http://localhost:8080/api/personas/$PERSONA_ID/profesiones | jq
# Esperado: 201, activo: true, fechaDesde: hoy

ELECTRICISTA_ID=2
curl -s -X POST -H "Authorization: Bearer $CAP_TOKEN" -H "Content-Type: application/json" \
  -d "{\"profesionId\":$ELECTRICISTA_ID}" \
  http://localhost:8080/api/personas/$PERSONA_ID/profesiones | jq

# Duplicado activo
curl -s -o /dev/null -w "%{http_code}\n" -X POST -H "Authorization: Bearer $CAP_TOKEN" \
  -H "Content-Type: application/json" -d "{\"profesionId\":$MECANICO_ID}" \
  http://localhost:8080/api/personas/$PERSONA_ID/profesiones
# Esperado: 409 PERSONA_PROFESION_YA_ASIGNADA
```

## 4. US3 — Consultar las profesiones de la persona

```bash
curl -s -H "Authorization: Bearer $CAP_TOKEN" \
  http://localhost:8080/api/personas/$PERSONA_ID/profesiones | jq
# Esperado: incluye Mecánico y Electricista, ambas activas
```

## 5. US4 — Directorio por profesión

```bash
curl -s -H "Authorization: Bearer $CAP_TOKEN" \
  "http://localhost:8080/api/profesiones/$MECANICO_ID/personas" | jq
# Esperado: la persona aparece con id, nombreCompleto, fechaDesde, cedula (sin más datos personales)
```

## 6. US5 — Retirar una asignación

```bash
ASIGNACION_ID="<id-de-la-asignacion-de-mecanico-obtenido-en-el-paso-3>"

curl -s -X PATCH -H "Authorization: Bearer $CAP_TOKEN" \
  http://localhost:8080/api/personas/$PERSONA_ID/profesiones/$ASIGNACION_ID/retirar | jq
# Esperado: 200, activo: false

curl -s -H "Authorization: Bearer $CAP_TOKEN" \
  "http://localhost:8080/api/profesiones/$MECANICO_ID/personas" | jq
# Esperado: la persona YA NO aparece

curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  "http://localhost:8080/api/personas/$PERSONA_ID/profesiones?incluirRetiradas=true" | jq
# Esperado: la asignación retirada de Mecánico sigue siendo consultable por ADMIN
```

## 7. Desactivar bloquea asignaciones nuevas, pero no retira las existentes

```bash
curl -s -X PATCH -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/profesiones/$ELECTRICISTA_ID/desactivar | jq

# Ya no se puede asignar
curl -s -o /dev/null -w "%{http_code}\n" -X POST -H "Authorization: Bearer $CAP_TOKEN" \
  -H "Content-Type: application/json" -d "{\"profesionId\":$ELECTRICISTA_ID}" \
  http://localhost:8080/api/personas/$PERSONA_ID/profesiones
# Esperado: 409 PROFESION_DESACTIVADA

# La persona conserva la asignación previa de Electricista
curl -s -H "Authorization: Bearer $CAP_TOKEN" \
  http://localhost:8080/api/personas/$PERSONA_ID/profesiones | jq
# Esperado: Electricista sigue apareciendo activa
```

## 8. Borrado lógico / restauración de la persona

```bash
curl -s -X DELETE -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/personas/$PERSONA_ID

curl -s -H "Authorization: Bearer $CAP_TOKEN" \
  "http://localhost:8080/api/profesiones/$MECANICO_ID/personas" | jq
# (si se reasigna Mecánico antes de este paso) Esperado: la persona no aparece

curl -s -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/personas/$PERSONA_ID/restaurar

# Reaparece en los directorios de sus profesiones activas
```

## Criterio de éxito del quickstart

- Paso 2 demuestra SC-002 y el control de acceso (US1).
- Pasos 3-4 demuestran SC-003/SC-001 (US2, US3).
- Paso 5 demuestra US4 (directorio) y SC-003.
- Paso 6 demuestra US5 y que el histórico retirado sigue siendo consultable
  por ADMIN.
- Paso 7 demuestra SC-004 (bloqueo por profesión desactivada) sin afectar
  asignaciones existentes.
- Paso 8 demuestra SC-006 (borrado lógico/restauración afecta el
  directorio, no las asignaciones).
- SC-007/SC-008 (permisos completos, suite existente en verde) se validan
  en pruebas automatizadas.

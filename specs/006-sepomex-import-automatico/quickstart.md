# Quickstart: Validación de la Automatización del Catálogo SEPOMEX

Guía para validar de extremo a extremo, una vez implementado este feature **y**
`002-autenticacion-autorizacion` (ver `plan.md` § Dependencia). Referencia:
`contracts/catalogo-importacion-api.md`, `data-model.md`.

## Prerrequisitos

- `002` implementado y funcionando (login, rol ADMIN).
- Migración `V6__create_catalogo_importacion.sql` aplicada.
- Variables configuradas: `SEPOMEX_IMPORT_CRON` (opcional, default semanal),
  `SEPOMEX_DIRECTORIO_ENTRADA`, `SEPOMEX_DIRECTORIO_PROCESADOS`.

## 1. Preparar token de ADMIN

```bash
ADMIN_TOKEN=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"login":"admin","password":"..."}' http://localhost:8080/login | jq -r '.accessToken')
```

## 2. US2 — Disparo manual e idempotencia visible

```bash
curl -s -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  -F "archivo=@src/main/resources/catalogoSepomex.csv" \
  http://localhost:8080/api/codigos-postales/importaciones | jq
```

**Esperado**: 200 con `insertados > 0` (primera carga).

```bash
# Subir el mismo archivo de nuevo
curl -s -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  -F "archivo=@src/main/resources/catalogoSepomex.csv" \
  http://localhost:8080/api/codigos-postales/importaciones | jq '.insertados, .actualizados, .sinCambio'
```

**Esperado**: `insertados: 0` (SC-002 — idempotencia visible); `sinCambio` igual al
total de filas del archivo.

```bash
curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $CAP_TOKEN" \
  -F "archivo=@src/main/resources/catalogoSepomex.csv" \
  http://localhost:8080/api/codigos-postales/importaciones
# Esperado: 403
```

## 3. Archivo corrupto

```bash
echo "esto no es un catalogo valido" > /tmp/corrupto.csv
curl -s -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  -F "archivo=@/tmp/corrupto.csv" \
  http://localhost:8080/api/codigos-postales/importaciones | jq
# Esperado: 400 CATALOGO_ARCHIVO_INVALIDO

# El catálogo no cambió
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/codigos-postales/06600 | jq
```

## 4. US1 — Ciclo programado

```bash
cp src/main/resources/catalogoSepomex.csv "$SEPOMEX_DIRECTORIO_ENTRADA/catalogo-2026-07.csv"
# Esperar (o disparar manualmente en pruebas) el siguiente ciclo del job
```

**Esperado**: el archivo se importa, se mueve a `$SEPOMEX_DIRECTORIO_PROCESADOS`, y
aparece en la bitácora con `origen: "PROGRAMADA"`, `usuario: null`.

## 5. US3 — Consultar bitácora

```bash
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/codigos-postales/importaciones | jq
```

**Esperado**: incluye las corridas de los pasos 2-4, cada una con su `origen`,
`estado`, resumen y (para la manual) `usuario`.

## 6. Concurrencia (SC-004)

```bash
# Disparar dos subidas casi simultáneas del mismo archivo grande
curl -s -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  -F "archivo=@src/main/resources/catalogoSepomex.csv" \
  http://localhost:8080/api/codigos-postales/importaciones > /tmp/r1.json &
curl -s -o /dev/null -w "%{http_code}\n" -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  -F "archivo=@src/main/resources/catalogoSepomex.csv" \
  http://localhost:8080/api/codigos-postales/importaciones
wait
# Uno de los dos responde 409 CATALOGO_IMPORTACION_EN_CURSO; el otro 200
```

## 7. Disponibilidad durante la importación (SC-005)

```bash
# Mientras el paso 6 corre, en otra terminal:
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/codigos-postales/06600
# Esperado: 200 (o 401/404 según autenticación/datos, pero nunca degradado por la importación en curso)
```

## Criterio de éxito del quickstart

- Paso 2 demuestra SC-002 y el control de acceso (US2).
- Paso 3 demuestra SC-003 (catálogo intacto ante archivo corrupto).
- Paso 4 demuestra SC-001 (ciclo programado).
- Paso 5 demuestra la bitácora completa (US3).
- Paso 6 demuestra SC-004 (candado de concurrencia).
- Paso 7 demuestra SC-005 (sin degradación de lectura durante la carga).
- SC-006/SC-007 (control de acceso completo, suite existente en verde) se validan en
  pruebas automatizadas.

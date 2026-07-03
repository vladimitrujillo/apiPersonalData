# Quickstart: Validación de Gestión de Personas y Catálogo de Códigos Postales

Guía para validar de extremo a extremo que el feature funciona, una vez implementado.
Referencia: `contracts/*.md` para los esquemas exactos de request/response y
`data-model.md` para las entidades.

## Prerrequisitos

- Java 21, Maven, Docker (para PostgreSQL local / Testcontainers).
- Variable de entorno `API_KEY` configurada (ver `research.md` §6) y enviada en el header
  `X-API-Key` en cada llamada de este quickstart.
- Migraciones Flyway aplicadas (arranque de la aplicación las ejecuta automáticamente).
- Catálogo SEPOMEX importado al menos una vez (ver paso 2).

## 1. Levantar la aplicación y la base de datos

```bash
docker compose up -d db          # PostgreSQL local (o Testcontainers en pruebas)
./mvnw spring-boot:run
```

## 2. Ejecutar la importación del catálogo SEPOMEX (idempotente)

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--import-sepomex=/ruta/al/catalogo.csv"
```

**Validar idempotencia (SC-005)**: ejecutar el mismo comando una segunda vez y confirmar
que el conteo de filas en `codigo_postal` y `colonia` no cambia.

```bash
psql -h localhost -U app -d personas -c "SELECT count(*) FROM codigo_postal;"
psql -h localhost -U app -d personas -c "SELECT count(*) FROM colonia;"
# repetir el import y volver a contar — deben coincidir
```

## 3. US3 — Consultar un código postal exacto

```bash
curl -s -H "X-API-Key: $API_KEY" http://localhost:8080/api/codigos-postales/06600 | jq
```

**Esperado**: 200 con `estado`, `municipio` y lista de `colonias` (ver
`contracts/codigos-postales-api.md`).

```bash
curl -s -o /dev/null -w "%{http_code}\n" -H "X-API-Key: $API_KEY" \
  http://localhost:8080/api/codigos-postales/00000
# Esperado: 404 (CP_NO_ENCONTRADO)

curl -s -o /dev/null -w "%{http_code}\n" -H "X-API-Key: $API_KEY" \
  http://localhost:8080/api/codigos-postales/ABCDE
# Esperado: 400 (CP_FORMATO_INVALIDO)
```

## 4. US4 — Autocompletado de colonias

```bash
curl -s -H "X-API-Key: $API_KEY" \
  "http://localhost:8080/api/colonias?nombre=roma&estado=Ciudad%20de%20M%C3%A9xico" | jq
```

**Esperado**: 200 con lista de colonias cuyo nombre contiene "roma" dentro de ese estado;
lista vacía `[]` si no hay coincidencias (nunca error).

## 5. US1 — Ciclo de vida completo de una persona (con integración de dirección, US5)

```bash
# Crear
PERSONA_ID=$(curl -s -X POST -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{
    "nombres": "Juana",
    "apellidos": "Pérez López",
    "fechaNacimiento": "1990-05-10",
    "sexo": "F",
    "curp": "PELJ900510MDFRZN09",
    "rfc": "PELJ900510AB1",
    "correo": "juana.perez@example.com",
    "telefono": "5512345678",
    "direccion": {
      "calle": "Av. Insurgentes",
      "numero": "100",
      "colonia": "Roma Norte",
      "codigoPostal": "06700",
      "pais": "MX"
    }
  }' http://localhost:8080/api/personas | jq -r '.id')

echo "Persona creada: $PERSONA_ID"

# Consultar (verificar que municipio/estado se autocompletaron — US5)
curl -s -H "X-API-Key: $API_KEY" http://localhost:8080/api/personas/$PERSONA_ID | jq

# Actualizar parcialmente
curl -s -X PATCH -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{"telefono": "5587654321"}' \
  http://localhost:8080/api/personas/$PERSONA_ID | jq

# Eliminar (borrado lógico)
curl -s -X DELETE -H "X-API-Key: $API_KEY" -o /dev/null -w "%{http_code}\n" \
  http://localhost:8080/api/personas/$PERSONA_ID
# Esperado: 204

# Confirmar que ya no se puede consultar (borrado lógico, no físico)
curl -s -o /dev/null -w "%{http_code}\n" -H "X-API-Key: $API_KEY" \
  http://localhost:8080/api/personas/$PERSONA_ID
# Esperado: 404

# Confirmar en base de datos que el registro sigue existiendo (no borrado físico)
psql -h localhost -U app -d personas \
  -c "SELECT id, deleted_at FROM persona WHERE id = '$PERSONA_ID';"
# Esperado: una fila, con deleted_at NOT NULL
```

## 6. Casos de error a validar (SC-002)

```bash
# Correo duplicado
curl -s -X POST -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{ "...": "mismo correo que una persona activa existente" }' \
  http://localhost:8080/api/personas | jq
# Esperado: 409 PERSONA_CORREO_DUPLICADO

# Fecha de nacimiento futura
curl -s -X POST -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{ "fechaNacimiento": "2999-01-01", "...": "..." }' \
  http://localhost:8080/api/personas | jq
# Esperado: 400 FECHA_NACIMIENTO_FUTURA

# CP mexicano inexistente en la dirección
curl -s -X POST -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{ "direccion": { "codigoPostal": "00000", "pais": "MX", "...": "..." }, "...": "..." }' \
  http://localhost:8080/api/personas | jq
# Esperado: 404 CP_NO_ENCONTRADO

# Colonia que no pertenece al CP dado
curl -s -X POST -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{ "direccion": { "codigoPostal": "06700", "colonia": "Colonia Inexistente En Ese CP", "pais": "MX", "...": "..." }, "...": "..." }' \
  http://localhost:8080/api/personas | jq
# Esperado: 400 COLONIA_NO_VALIDA_PARA_CP
```

## 7. US2 — Listado con paginación y filtros

```bash
curl -s -H "X-API-Key: $API_KEY" \
  "http://localhost:8080/api/personas?municipio=Cuauht%C3%A9moc&page=0&size=20" | jq
```

**Esperado**: 200 con `contenido`, `pagina`, `tamanoPagina`, `totalElementos`,
`totalPaginas`; personas eliminadas lógicamente ausentes de `contenido`.

## Criterio de éxito del quickstart

- Los pasos 3–7 responden con los códigos HTTP y estructuras documentadas en
  `contracts/*.md`.
- El paso 5 demuestra el ciclo completo crear → consultar → actualizar → eliminar
  (SC-001) y que el borrado es lógico, no físico (SC-003).
- El paso 2 demuestra idempotencia de la importación del catálogo (SC-005).

# Research: Automatización de la Actualización del Catálogo SEPOMEX

## 1. Extensión mínima de `SepomexImportService` (resumen por categoría + tolerancia por fila)

**Decision**: `importar(Path)` conserva su firma y su rol (parsear el archivo,
delegar el upsert a `CpCatalogoRepository`), pero cambia internamente en dos puntos:

1. **Validación estructural previa** (FR-011): antes de upsertear cualquier fila, se
   lee el archivo completo una vez para validar que el encabezado sea el esperado y que
   cada línea tenga exactamente 6 columnas separadas por `|`. Si alguna línea no cumple,
   se lanza una excepción de negocio nueva (`CatalogoArchivoInvalidoException`) **antes**
   de llamar a `upsert(...)` ni una sola vez — el catálogo queda intacto por
   construcción (nada se escribió), no por rollback de una transacción a medias.
2. **Tolerancia por fila** (FR-012): superada la validación estructural, cada fila se
   procesa individualmente; una fila con un valor inválido en un campo (p. ej. un
   código postal que no son 5 dígitos) se cuenta como rechazada y se continúa con la
   siguiente, en vez de abortar el archivo completo (comportamiento actual).

El método pasa de devolver `int` a devolver un `record ResumenImportacion(int
insertados, int actualizados, int sinCambio, int rechazados, List<String>
detallesRechazados)`.

**Rationale**: Es el cambio mínimo indispensable para que el disparo manual pueda
reportar el resumen que pide la spec (FR-006) y para que un archivo con algunas filas
inválidas no bloquee la actualización del resto (FR-012), sin tocar la clave natural ni
el `ON CONFLICT` de `CpCatalogoRepository.upsert(...)` (research.md §2) — exactamente la
instrucción del usuario de "no cambiar su lógica de upsert".

**Alternatives considered**:
- Mantener `importar()` como todo-o-nada (como hoy) y calcular el resumen de otra
  forma: rechazada — FR-012 pide explícitamente que una fila inválida no aborte el
  resto; es un requisito de comportamiento, no solo de reporte.
- Dos pasadas físicas separadas del archivo (una para validar estructura, otra para
  upsertear): es exactamente lo que se hace (research.md, arriba) — se documenta aquí
  como la decisión, no como alternativa rechazada, dado el volumen esperado (~150k
  filas, manejable en memoria/dos lecturas de stream sin problema).

## 2. Distinguir insertado / actualizado / sin cambio en el upsert

**Decision**: `CpCatalogoRepository.upsert(...)` cambia su tipo de retorno de `void` a
`Optional<Boolean>` (`true` = fue un `INSERT`, `false` = fue un `UPDATE` con cambio real,
vacío = la fila ya existía con esos mismos valores, "sin cambio"), usando dos técnicas
nativas de PostgreSQL sobre la misma sentencia `INSERT ... ON CONFLICT ... DO UPDATE`
que ya existe:

```sql
INSERT INTO cp_catalogo (...) VALUES (...)
ON CONFLICT (codigo_postal, id_asenta_cpcons) DO UPDATE SET
    estado = EXCLUDED.estado,
    municipio = EXCLUDED.municipio,
    asentamiento = EXCLUDED.asentamiento,
    tipo_asentamiento = EXCLUDED.tipo_asentamiento,
    updated_at = now()
WHERE (cp_catalogo.estado, cp_catalogo.municipio, cp_catalogo.asentamiento, cp_catalogo.tipo_asentamiento)
      IS DISTINCT FROM (EXCLUDED.estado, EXCLUDED.municipio, EXCLUDED.asentamiento, EXCLUDED.tipo_asentamiento)
RETURNING (xmax = 0) AS insertado
```

- `WHERE ... IS DISTINCT FROM ...` en la cláusula `DO UPDATE` hace que, si el valor
  entrante es idéntico al ya almacenado, la sentencia no actualice nada y no devuelva
  fila (`RETURNING` vacío ⇒ "sin cambio").
- `xmax = 0` es la forma estándar de Postgres para distinguir, sobre una fila devuelta
  por `RETURNING`, si vino de la rama `INSERT` (`xmax = 0`) o de la rama `DO UPDATE`
  (`xmax <> 0`).

**Rationale**: Es una extensión mínima y focalizada de la misma sentencia — la clave de
conflicto (`codigo_postal, id_asenta_cpcons`) y los campos que se actualizan no cambian
en absoluto; solo se añade una guarda `WHERE` (para detectar "sin cambio") y una
proyección `RETURNING` (para distinguir insertado/actualizado). Es la única forma atómica
de obtener esta distinción sin ejecutar un `SELECT` adicional antes del `INSERT` (que
introduciría una condición de carrera entre leer y escribir).

**Alternatives considered**:
- Hacer un `SELECT` antes del `upsert` para comparar valores y decidir la categoría en
  Java: rechazada — no atómico (dos sentencias en vez de una, ventana de carrera bajo
  escritura concurrente) y más lento sin necesidad.
- No distinguir "sin cambio" de "actualizado" (fusionar ambas categorías): rechazada —
  la spec (FR-006) pide las cuatro categorías explícitamente.

## 3. Candado de ejecución única: advisory lock de PostgreSQL, con alcance de transacción

**Decision**: `pg_try_advisory_xact_lock(:clave)` (no `pg_try_advisory_lock`/sesión),
invocado al inicio de la misma transacción que ejecuta toda la corrida (validación +
upserts + inserción de la fila de bitácora). Si devuelve `false`, la corrida no procede:
se registra en la bitácora como rechazada por concurrencia (en una transacción corta,
separada) y, si el origen es manual, el endpoint responde `409
CATALOGO_IMPORTACION_EN_CURSO`. La clave numérica es una constante fija (p. ej. el hash
de un texto identificador único de este proceso, calculado una sola vez en código).

**Rationale**: Cumple la instrucción explícita del usuario — candado en base de datos,
no en memoria, para soportar más de una instancia de la aplicación. La variante
`_xact_lock` se libera automáticamente al terminar la transacción (`COMMIT` o
`ROLLBACK`), lo cual es más seguro que la variante de sesión (`pg_advisory_lock`) bajo
un pool de conexiones (HikariCP): con la variante de sesión, un candado no liberado
explícitamente (p. ej. por una excepción no prevista) podría quedar retenido en una
conexión que vuelve al pool y se reutiliza, bloqueando todas las importaciones futuras
hasta reiniciar la aplicación. La variante transaccional no tiene ese riesgo.

**Alternatives considered**:
- `pg_try_advisory_lock`/`pg_advisory_unlock` (alcance de sesión): rechazada por el
  riesgo de fuga descrito arriba bajo un pool de conexiones.
- Una tabla de "lock" con una fila y `SELECT ... FOR UPDATE NOWAIT`: funcionalmente
  equivalente, pero un advisory lock nativo no requiere una tabla ni una fila que
  mantener/migrar — más simple (Principio IV) para un candado que no necesita guardar
  ningún dato, solo coordinar exclusión mutua.

## 4. Endpoint de subida manual: validación de tamaño y estructura antes de importar

**Decision**: `POST /api/codigos-postales/importaciones` recibe un `MultipartFile`.
Antes de invocar la importación:

1. Tamaño máximo: delegado a la configuración estándar de Spring
   (`spring.servlet.multipart.max-file-size`/`max-request-size`, configurable por
   variable de entorno); si se excede, Spring lanza `MaxUploadSizeExceededException`,
   capturada en `GlobalExceptionHandler` y mapeada a `400
   CATALOGO_ARCHIVO_DEMASIADO_GRANDE`.
2. El archivo subido se escribe a un archivo temporal (`Files.createTempFile`) y se
   delega al **mismo** `importar(Path)` que usa el job programado — un solo camino de
   código para la lógica de importación real, sin duplicar el parseo para el caso
   manual (Principio IV).

**Rationale**: Reutiliza la configuración nativa de Spring Boot para el límite de
tamaño (sin código propio de validación de tamaño) y evita mantener dos
implementaciones del parseo de archivo (una para `Path`, otra para `MultipartFile`).

**Alternatives considered**:
- Leer el `MultipartFile` directamente como stream sin escribir a disco: rechazada por
  ahora — `importar(Path)` ya asume un `Path` (usado también por el job programado y
  por `SepomexImportRunner`); introducir una sobrecarga que acepte un `Reader` es una
  opción válida pero menos mínima que reutilizar el mismo método vía un archivo
  temporal.

## 5. Determinar si un archivo ya fue procesado: hash de contenido, no el nombre

**Decision**: Antes de importar un archivo encontrado en el directorio observado, el
job programado calcula su hash SHA-256 (una lectura completa del archivo — mismo costo
ya aceptado en research.md §1 para la validación estructural) y consulta
`catalogo_importacion` por una corrida previa **exitosa** con ese mismo `archivo_hash`.
Solo si no hay ninguna, procede a importar y, al finalizar con éxito, archiva el
archivo. `archivo` (el nombre) se conserva en la bitácora únicamente como dato legible
para el operador (US3); el `archivo_hash` es la clave real de "¿ya se procesó esto?".
Si el archivado físico fallara después de una corrida exitosa, el archivo seguiría en
el directorio, pero la consulta por hash ya lo excluye de futuros ciclos (spec Edge
Cases).

**Rationale**: El nombre de archivo no es una clave confiable para "ya procesado": el
catálogo oficial de SEPOMEX se publica típicamente bajo un nombre fijo (p. ej.
`CPdescarga.txt`), de modo que cada actualización periódica legítima llegaría con el
**mismo nombre y contenido nuevo** — un matching por nombre trataría cada actualización
real como "ya procesada" y la saltaría siempre después de la primera vez, rompiendo
silenciosamente el objetivo central de US1 (automatizar las actualizaciones
periódicas). El hash de contenido no tiene ese problema: solo coincide cuando el
contenido es *exactamente* el mismo ya importado con éxito, sin importar el nombre.
Calcularlo no añade una lectura adicional relevante (el archivo ya se lee completo una
vez para la validación estructural, research.md §1) y reutiliza el mismo patrón
`MessageDigest`/SHA-256 ya usado en `JwtService.hashSha256(String)` (aplicado aquí sobre
los bytes del archivo en vez de un `String`).

**Alternatives considered**:
- Matching por nombre de archivo (la redacción original de la spec/plan, dejada
  ambigua con "o su hash"): rechazada — ver Rationale, falla exactamente en el caso más
  común y realista (SEPOMEX republicando bajo el mismo nombre).
- Matching por nombre **y** hash (ambos deben coincidir): rechazada por innecesaria —
  si el contenido es idéntico, no importa si el nombre cambió (p. ej. un archivo
  archivado con timestamp en el nombre); el hash solo ya es suficiente y más simple
  (Principio IV).

## 6. Invalidación de caché

**Decision**: `CatalogoImportacionOrchestrator` invoca `@CacheEvict(value =
"codigosPostales", allEntries = true)` (mismo nombre de caché ya usado por
`CodigoPostalService.consultarPorCodigoPostal`) únicamente cuando la corrida termina
con estado de éxito y al menos un cambio aplicado (insertado o actualizado > 0). Una
corrida sin cambios (todo "sin cambio") no necesita evicción (nada cambió que pudiera
estar cacheado de forma obsoleta), y una corrida fallida tampoco evict (FR-014).

**Rationale**: Reutiliza el mecanismo de caché ya existente (`specs/001.../research.md`
§7) sin introducir un backend de caché nuevo; la evicción total (`allEntries = true`)
es más simple que invalidar entradas individuales por CP (Principio IV), y el volumen
de la caché (~150k CPs como máximo) hace que recalentarla sea barato.

## 7. Programación del job

**Decision**: `@EnableScheduling` + `@Scheduled(cron = "${app.sepomex.import-cron:0 0 3
* * MON}")` (default: lunes 3:00 am, "semanal"), leyendo el directorio observado y el
directorio de archivado desde `app.sepomex.directorio-entrada` /
`app.sepomex.directorio-procesados` (variables de entorno, mismo estilo que
`app.security.api-key` existente).

**Rationale**: `@Scheduled` con expresión cron configurable es el mecanismo estándar de
Spring Boot para tareas periódicas, ya disponible sin dependencias nuevas (Principio
IV), y una expresión cron es más flexible que un simple intervalo fijo para expresar
"semanal" de forma ajustable (p. ej. cambiar el día/hora sin tocar código).

## Resumen de resolución de NEEDS CLARIFICATION

No quedaban marcadores `NEEDS CLARIFICATION` pendientes en el Technical Context (la
única ambigüedad de la especificación — rechazar vs. encolar una importación
concurrente — ya se resolvió en `/speckit-specify` antes de este plan: rechazar de
inmediato, sin cola).

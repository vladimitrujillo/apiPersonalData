# Research: Gestión de Personas y Catálogo de Códigos Postales

Todas las decisiones de stack tecnológico fueron especificadas explícitamente por el
solicitante (Java 21, Spring Boot 3.x, PostgreSQL, Flyway, MapStruct, springdoc-openapi,
JUnit 5/Mockito, Maven). No quedan marcadores `NEEDS CLARIFICATION` en el Technical
Context. Este documento resuelve las decisiones de diseño e implementación necesarias
para aplicar ese stack de forma consistente con la constitución del proyecto.

## 1. Borrado lógico de Persona

**Decision**: Columna `activo BOOLEAN NOT NULL DEFAULT true` en la tabla `persona`.
`activo = true` significa que la persona está vigente; `activo = false` es el estado tras
un borrado lógico. Se evita un filtro Hibernate global (`@SQLRestriction`/`@Where`) como
única fuente de verdad; en su lugar, los métodos de repositorio son explícitos
(`findByIdAndActivoTrue`, `findAllActivo(...)`, etc.).

*(Nota: esta decisión reemplaza la exploración inicial de `deleted_at TIMESTAMP`, en línea
con el modelo de datos de referencia provisto — flag `activo` en vez de timestamp.)*

**Rationale**: Cumple FR-005/FR-012 (el registro se conserva, desaparece de listados y
consultas). Un flag booleano simple es la representación mínima suficiente (Principio IV)
y es la especificada en el modelo de datos de referencia. Evitar el filtro global implícito
de Hibernate reduce sorpresas y facilita auditar qué consultas respetan el borrado lógico.

**Alternatives considered**:
- `deleted_at TIMESTAMP NULL`: descartada en favor del flag `activo`, que es más simple
  cuando no se requiere conocer el momento exacto de eliminación (no forma parte del spec).
- Filtro `@Where`/`@SQLRestriction` global a nivel de entidad: rechazado como único
  mecanismo porque oculta la condición y dificulta pruebas explícitas del comportamiento
  de FR-012; puede añadirse como refuerzo pero no como única fuente de verdad.
- Tabla separada de "personas eliminadas" (soft-delete por movimiento de fila): rechazada
  por complejidad innecesaria (viola Principio IV) para un caso de uso simple de flag.

## 2. Unicidad de correo/CURP solo entre activos

**Decision**: Restricción de unicidad a nivel de aplicación (validación en el `service`
antes de persistir) en lugar de un `UNIQUE` constraint de base de datos sobre la columna
directa, ya que PostgreSQL no soporta unicidad condicional simple sin índice parcial.
Se implementa como **índice único parcial** en Flyway:
`CREATE UNIQUE INDEX ux_persona_correo_activo ON persona (correo) WHERE activo = true;`
(y equivalente para `curp`). La verificación en el `service` produce el error de negocio
claro (FR-006/FR-007); el índice parcial es la garantía de integridad a nivel de datos
ante condiciones de carrera.

**Rationale**: Un índice único parcial garantiza la regla "único solo entre activos"
(spec Assumptions) de forma atómica, evitando duplicados por condiciones de carrera que
una validación únicamente a nivel de aplicación no puede prevenir.

**Alternatives considered**:
- `UNIQUE` constraint simple sobre `correo`/`curp`: rechazado porque impediría reutilizar
  el correo/CURP de una persona eliminada lógicamente, contradiciendo la decisión de
  Assumptions.
- Solo validación a nivel de aplicación sin índice: rechazada por riesgo de condición de
  carrera bajo escritura concurrente.

## 3. Estrategia de importación idempotente del catálogo SEPOMEX (tabla `cp_catalogo`)

**Decision**: Siguiendo el modelo de datos de referencia, el catálogo se representa como
una **única tabla plana** `cp_catalogo` (no normalizada en CódigoPostal + Colonia),
reflejando la estructura real del archivo fuente de SEPOMEX: una fila por cada combinación
de código postal y asentamiento, con columnas `codigo_postal`, `estado`, `municipio`,
`asentamiento` (nombre de la colonia), `tipo_asentamiento` e `id_asenta_cpcons` (el
identificador consecutivo de asentamiento dentro del CP, tal como lo provee SEPOMEX).
El proceso de importación (`SepomexImportService`, ejecutable vía comando
administrativo/perfil de arranque, no vía endpoint HTTP público) ejecuta un **upsert**:
`INSERT INTO cp_catalogo (...) VALUES (...) ON CONFLICT (codigo_postal, id_asenta_cpcons) DO UPDATE SET ...`.
La restricción `UNIQUE (codigo_postal, id_asenta_cpcons)` es la clave natural de
idempotencia: reimportar el mismo archivo no duplica filas, y una fila cuyo contenido
cambió en una versión nueva del catálogo se actualiza en su lugar.

**Rationale**: Cumple FR-017 y el Principio VI (idempotente, sin duplicar registros,
refleja la versión más reciente), usando la clave natural del propio catálogo SEPOMEX
(`codigo_postal` + `id_asenta_cpcons`) en vez de inventar una clave sintética, lo cual
simplifica el proceso de carga (Principio IV) y facilita razonar sobre la idempotencia:
mismo archivo de origen → mismo conjunto de claves `(codigo_postal, id_asenta_cpcons)` →
mismo resultado tras el upsert.

**Alternatives considered**:
- Modelo normalizado en dos tablas (`codigo_postal` 1:N `colonia`) explorado inicialmente:
  descartado en favor de la tabla plana `cp_catalogo`, que es más simple (Principio IV),
  evita una segunda tabla con FK sintética y mapea 1:1 con el formato de archivo real de
  SEPOMEX (más fácil de importar y de razonar sobre la idempotencia).
- Truncar toda la tabla y recargar: rechazado porque genera una ventana de
  indisponibilidad del catálogo completo y no es incremental; más riesgoso operativamente.
- Ignorar duplicados (`INSERT ... ON CONFLICT DO NOTHING`): rechazado porque no reflejaría
  actualizaciones de una versión más nueva del catálogo (viola el edge case de spec sobre
  reimportación con cambios).

## 3b. Índices sobre `cp_catalogo`

**Decision**: Dos índices, según el modelo de datos de referencia:
- `CREATE INDEX ix_cp_catalogo_codigo_postal ON cp_catalogo (codigo_postal);` — acelera la
  consulta exacta por CP (US3, FR-013).
- `CREATE INDEX ix_cp_catalogo_estado_municipio_asenta ON cp_catalogo (estado, municipio, asentamiento);`
  — acelera la búsqueda de colonias acotada por estado/municipio (US4, FR-016).

**Rationale**: Cubren directamente los dos patrones de acceso del catálogo (lookup exacto
por CP, y filtrado por estado/municipio antes de aplicar la coincidencia parcial de
nombre). Suficiente para el volumen esperado (~150k filas, research.md original §Scale)
sin introducir infraestructura adicional.

**Alternatives considered**:
- Índice `pg_trgm` (trigram) sobre `asentamiento` para acelerar `LIKE '%texto%'` con
  comodín inicial: no incluido en el modelo de datos de referencia; se documenta aquí como
  posible optimización futura si el rendimiento de la búsqueda por nombre parcial (SC-007)
  no es suficiente solo con el índice compuesto, pero se deja fuera del alcance actual
  (Principio IV — no anticipar necesidades no confirmadas).

## 3c. Relación `direccion` ↔ `cp_catalogo` (snapshot + FK opcional)

**Decision**: `direccion` es una tabla propia con FK obligatoria `persona_id` (relación
1:N declarada a nivel de esquema) y columnas de **snapshot de texto** `colonia`,
`municipio`, `estado` (copiadas al momento de guardar/actualizar la dirección), más una FK
**opcional** `cp_catalogo_id` que referencia la fila de `cp_catalogo` usada para validar y
autocompletar (solo cuando `pais = MX` y el CP existe en el catálogo). Cuando `pais != MX`,
`cp_catalogo_id` queda `NULL` y `colonia`/`municipio`/`estado` se guardan tal como los envía
el cliente (FR-022).

*(Nota sobre cardinalidad: el modelo de datos de referencia declara `direccion` como 1:N
respecto a `persona` a nivel de esquema, lo cual deja espacio para un historial de
direcciones en el futuro sin migración adicional. El spec de este feature solo requiere una
dirección vigente por persona; en el alcance actual, el `service` mantiene exactamente una
fila de `direccion` "vigente" por persona — la más reciente — y no expone historial. Si se
requiere historial de direcciones, es una ampliación de alcance para un feature futuro.)*

**Rationale**: El snapshot de texto evita que una reimportación del catálogo (que puede
modificar o eliminar una colonia, ver §3) altere silenciosamente direcciones ya guardadas de
personas; la FK opcional conserva la trazabilidad hacia la fila exacta del catálogo usada en
el momento de la validación, útil para auditoría o para volver a autocompletar si el cliente
lo solicita explícitamente. Es coherente con FR-019/FR-020/FR-021 (validar, autocompletar,
validar colonia) sin acoplar permanentemente la dirección de una persona a la disponibilidad
futura de esa fila del catálogo.

**Alternatives considered**:
- FK obligatoria (NOT NULL) a `cp_catalogo` sin snapshot de texto: rechazada porque no
  admite direcciones fuera de México (FR-022) y porque una reimportación que borre/cambie
  una fila del catálogo podría dejar direcciones existentes sin referencia válida.
- Solo snapshot de texto, sin FK: rechazada porque pierde trazabilidad hacia el registro
  exacto del catálogo usado, dificultando auditoría y volver a autocompletar bajo demanda.

## 4. Validación de formatos (CURP, RFC, teléfono, correo, código postal)

**Decision**: Bean Validation (Jakarta) con anotaciones `@Pattern` en los DTOs de entrada:
- CURP: `^[A-Z]{4}\d{6}[HM][A-Z]{5}[A-Z0-9]{2}$` (18 caracteres, formato oficial RENAPO).
- RFC: `^[A-ZÑ&]{3,4}\d{6}[A-Z0-9]{3}$` (persona física, 13 caracteres).
- Correo: `@Email` de Jakarta Validation.
- Teléfono: `^\d{10}$` (10 dígitos nacionales MX, según Assumptions del spec).
- Código postal: `^\d{5}$` (FR-014).

**Rationale**: Bean Validation centraliza la sanitización/validación en la capa de entrada
(Principio III), produce mensajes de error consistentes que el `@ControllerAdvice` traduce
al formato JSON único (Principio V), y es la herramienta estándar ya elegida por el
solicitante.

**Alternatives considered**:
- Validación manual en el `service`: rechazada por duplicar lo que Bean Validation ya
  resuelve declarativamente y por dispersar las reglas de validación fuera de la capa de
  entrada.

## 5. Manejo global de errores y formato de respuesta

**Decision**: `@RestControllerAdvice` con un DTO único `ApiError { codigo: string, mensaje: string, detalles?: [{campo, motivo}] }`.
Mapeo: `MethodArgumentNotValidException`/`ConstraintViolationException` → 400 con
`detalles` por campo; excepciones de negocio (`DuplicateFieldException`, `RecursoNoEncontradoException`,
`ColoniaInvalidaException`) → 409/404/400 respectivamente, cada una con `codigo` estable
(p. ej. `PERSONA_CORREO_DUPLICADO`, `PERSONA_NO_ENCONTRADA`, `CP_NO_ENCONTRADO`,
`CP_FORMATO_INVALIDO`, `COLONIA_NO_VALIDA_PARA_CP`).

**Rationale**: Cumple el Principio V (formato consistente) y SC-002 (mensajes claros que
identifican campo y motivo). Los códigos de error estables permiten a los clientes manejar
casos programáticamente sin parsear el mensaje.

**Alternatives considered**:
- Usar directamente el formato `ProblemDetail` (RFC 7807) de Spring 6: considerado y
  compatible con "formato JSON consistente"; se documenta como opción equivalente en
  `contracts/error-format.md`, pero se mantiene un DTO propio simple para no atar el
  contrato público a los nombres de campo de RFC 7807 y facilitar incluir `detalles` por
  campo de forma directa.

## 6. Autenticación (API key de servicio)

**Decision**: Filtro `ApiKeyAuthFilter` (`OncePerRequestFilter`) que valida un header
`X-API-Key` contra un valor configurado (variable de entorno), aplicado a todos los
endpoints salvo el propio recurso de documentación OpenAPI/Swagger UI. Sin Spring Security
completo (evita complejidad de sesiones/roles no requerida por el spec); se usa un filtro
ligero registrado en la cadena de Spring Web.

**Rationale**: Cumple FR-023 y el Principio de Restricciones Adicionales de la constitución
(acceso a datos personales sujeto a autenticación) con la opción más simple suficiente
(Principio IV), en línea con la decisión "API key de servicio" documentada en las
Assumptions del spec.

**Alternatives considered**:
- Spring Security con roles de usuario: rechazada por ahora (fuera de alcance según
  Assumptions del spec); se deja como ampliación futura si se requieren roles.

## 7. Caché de respuestas del catálogo de códigos postales

**Decision**: `@Cacheable` (Spring Cache, backend `ConcurrentMapCache` en memoria, dejando
la puerta abierta a un backend distribuido como Redis en el futuro sin cambiar el código de
servicio) sobre la consulta exacta por CP; adicionalmente se agregan encabezados HTTP
`Cache-Control: max-age=...` en las respuestas de consulta de catálogo para permitir caché
en clientes/CDN.

**Rationale**: Cumple FR-018 y SC-004/SC-007 (respuestas aptas para caché, tiempos de
respuesta adecuados para autocompletado) sin introducir infraestructura adicional
(Principio IV).

**Alternatives considered**:
- Caché externo (Redis) desde el inicio: rechazado por ahora como complejidad no
  justificada por el alcance actual (Principio IV); la abstracción de Spring Cache permite
  añadirlo después sin refactor grande.

## 8. Prevención de fuga de datos personales en logs

**Decision**: (a) No registrar cuerpos completos de petición/respuesta (`spring.mvc.log-request-details=false`,
sin filtros de logging de payload); (b) los DTOs de log/auditoría explícitos, si se
agregan, excluyen CURP, RFC, correo, teléfono y dirección o los enmascaran parcialmente;
(c) el `GlobalExceptionHandler` nunca incluye el objeto de dominio completo en el mensaje
de error, solo el campo y motivo de validación.

**Rationale**: Cumple el Principio III (Privacidad por Diseño) de forma verificable — se
puede añadir una prueba que confirme que ningún log generado durante un flujo de creación
de persona contiene el valor de CURP/correo en texto plano.

**Alternatives considered**:
- Confiar en revisión manual de logs sin regla explícita: rechazada por no ser verificable
  ni auditable de forma automatizada.

## 9. Pruebas de integración con base de datos real

**Decision**: Testcontainers con imagen oficial `postgres` para pruebas de integración
(`@DataJpaTest` con `@Testcontainers`, y pruebas end-to-end con `@SpringBootTest`); H2 se
evita para pruebas de repositorio/integración por posibles diferencias de dialecto SQL
(especialmente índices parciales de la decisión #2), y se reserva únicamente, si acaso,
para pruebas muy simples que no dependan de comportamiento específico de PostgreSQL.

**Rationale**: El índice único parcial (`WHERE activo = true`) y el upsert
`ON CONFLICT` son específicos de PostgreSQL; probarlos contra H2 daría falsa confianza.
Usar Testcontainers con PostgreSQL real es más fiel a producción, consistente con el
Principio II (Test-First) exigiendo pruebas que reflejen el comportamiento real.

**Alternatives considered**:
- H2 en modo compatibilidad PostgreSQL: rechazada como fuente única de verdad por no
  soportar completamente índices parciales / sintaxis `ON CONFLICT` de forma idéntica;
  puede usarse opcionalmente para pruebas unitarias rápidas que no toquen esas
  características.

## Resumen de resolución de NEEDS CLARIFICATION

No había marcadores `NEEDS CLARIFICATION` pendientes en el Technical Context: todo el
stack fue provisto explícitamente por el solicitante. Las secciones anteriores documentan
las decisiones de diseño derivadas necesarias para implementar ese stack cumpliendo la
constitución del proyecto.

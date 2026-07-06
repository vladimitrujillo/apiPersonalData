# Research: Auditoría y Historial de Cambios en Personas

El mecanismo fue especificado explícitamente por el solicitante (Spring Data JPA
Auditing + `@MappedSuperclass` para "quién/cuándo"; tabla propia `persona_historial`
con diff en JSONB, preferida sobre Hibernate Envers). No quedan marcadores `NEEDS
CLARIFICATION` en el Technical Context. Este documento resuelve las decisiones de
diseño derivadas y documenta explícitamente por qué se prefirió tabla propia sobre
Envers, como pidió el solicitante.

## 1. Integración con `002`: forma del principal de autenticación

**Decision**: `SecurityAuditorAware implements AuditorAware<UUID>` obtiene el
`Authentication` actual de `SecurityContextHolder` y castea su `getPrincipal()` a un
objeto que expone directamente `usuario.id` (`UUID`), sin consultar `UsuarioRepository`
en cada escritura. Esto requiere que el `JwtAuthenticationFilter` de `002` establezca
como principal un objeto propio (p. ej. un `record UsuarioPrincipal(UUID id, String
login, Rol rol)`), no un `String` username plano.

**Rationale**: Es la forma más simple de tener el `id` disponible sin una consulta a BD
por cada `INSERT`/`UPDATE` auditado (Principio IV). Si `002` se implementa con un
principal `String` (solo login), este feature necesitaría una consulta adicional a
`UsuarioRepository` por cada escritura — funciona, pero es peor por una razón evitable.

**Nota de integración (no bloqueante para este plan, sí para `tasks.md` de `002`)**: al
escribir las tareas de implementación de `002`, el `JwtAuthenticationFilter` debe
construir el `Authentication` con este principal enriquecido. Es un ajuste menor sobre
el diseño ya planeado de `002` (que no llegó a especificar el tipo exacto del
principal), no una revisión de sus decisiones existentes.

**Alternatives considered**:
- `AuditorAware` resuelve el `id` con una consulta a `UsuarioRepository` a partir del
  login del principal: rechazada como mecanismo por defecto (un round-trip de BD extra
  en cada escritura auditada) aunque queda documentada como fallback válido si `002` se
  implementa con un principal `String` simple.

## 2. Consolidar `created_at`/`updated_at` bajo JPA Auditing

**Decision**: `Persona` y `Direccion` pasan a extender `@MappedSuperclass Auditable`,
que declara las cuatro columnas con anotaciones de Spring Data JPA Auditing:

```java
@CreatedBy   private UUID creadoPor;          // nullable
@CreatedDate private OffsetDateTime createdAt; // ya existente, mismo nombre de columna
@LastModifiedBy   private UUID actualizadoPor; // nullable
@LastModifiedDate private OffsetDateTime updatedAt; // ya existente, mismo nombre de columna
```

Se retira el manejo manual de `createdAt`/`updatedAt` en los constructores de `Persona`
y `Direccion` y el método `Persona.marcarActualizada()` (JPA Auditing actualiza
`updatedAt` automáticamente en cualquier `UPDATE`, incluidos los que solo tocan
`direccion`).

**Rationale**: El solicitante pidió expresamente `@CreatedDate`/`@LastModifiedDate`
junto con `@CreatedBy`/`@LastModifiedBy` "sobre una `@MappedSuperclass`". Tener las
fechas gestionadas a mano en un lado y por Auditing en otro sería dos mecanismos para el
mismo concepto (fecha de creación/modificación) — inconsistente y con riesgo de
divergencia (p. ej. olvidar llamar `marcarActualizada()` en un método nuevo). Unificar
bajo un solo `@MappedSuperclass` es la aplicación directa del Principio IV
(Simplicidad) al mecanismo que el propio solicitante eligió.

**Alternatives considered**:
- Dejar `createdAt`/`updatedAt` manuales y agregar `creadoPor`/`actualizadoPor` como
  columnas independientes gestionadas por un mecanismo distinto (p. ej. seteadas a mano
  en el `service` leyendo el `SecurityContext` directamente): rechazada — duplica en el
  `service` una responsabilidad que Spring Data JPA Auditing ya resuelve
  declarativamente para las cuatro columnas a la vez.

## 3. Nulabilidad de `creado_por`/`actualizado_por`

**Decision**: Ambas columnas nuevas son `NULL` (a diferencia de `created_at`/
`updated_at`, que siguen `NOT NULL`). Las filas de `persona`/`direccion` creadas antes de
este feature quedan con `creado_por`/`actualizado_por` en `NULL` (spec Assumptions);
toda fila creada después de este feature siempre tendrá `creado_por` no nulo, porque
todo endpoint de escritura ya exige autenticación (`002`).

**Rationale**: Migración puramente aditiva (Principio V) sin necesidad de backfill
inventado para datos históricos que nunca tuvieron un usuario real asociado.

## 4. Tabla propia vs Hibernate Envers para el historial

**Decision**: Tabla propia `persona_historial`, poblada explícitamente desde
`PersonaService` (nuevo `HistorialDiffService`) dentro de la misma transacción de cada
operación, en vez de Hibernate Envers.

**Rationale** (documentado explícitamente, como pidió el solicitante): Envers audita de
forma genérica copiando el estado completo de la entidad en cada revisión, en tablas
`_AUD` con su propia convención (no las de este proyecto) y requiere un
`RevisionListener`/conversores custom para (a) enmascarar CURP/RFC/teléfono antes de que
el valor toque la tabla de auditoría, y (b) producir el formato de diff campo-por-campo
con valor anterior/nuevo que pide la spec (FR-006). Para cuando ese `RevisionListener`
personalizado existe, ya es tanto código propio como un diff manual — pero corriendo
sobre la maquinaria genérica de Envers, con su modelo de revisiones global (compartido
entre todas las entidades auditadas) en vez de una tabla `persona_historial` legible y
acotada al dominio. Calcular el diff explícitamente en el `service` es más simple
(Principio IV) y da control total y directo sobre qué se enmascara y cómo se serializa,
sin pelear contra el modelo genérico de Envers.

**Alternatives considered**:
- Hibernate Envers con `RevisionListener` custom para enmascarado: rechazada por la
  razón anterior — la personalización necesaria anula la ventaja de "automático" de
  Envers.
- Triggers de PostgreSQL que registran cambios a nivel de fila: rechazados porque no
  tienen acceso al usuario autenticado de la capa de aplicación (`SecurityContext`) ni
  pueden aplicar el enmascarado de campos sensibles con la lógica de negocio ya presente
  en Java.

## 5. Enmascarado de campos sensibles en el historial

**Decision**: `MaskingUtil` (en `common.audit`) aplica un enmascarado parcial —
conserva los primeros 2 y los últimos 2 caracteres, sustituye el resto por `*` — a
CURP, RFC y teléfono, **antes** de construir el JSON que se persiste en
`persona_historial.cambios`. Ejemplo: CURP `PELJ900510MDFRZN09` → `PE***************09`;
teléfono `5512345678` → `55******78`. El enmascarado se aplica tanto al valor anterior
como al nuevo.

**Rationale**: Cumple FR-007 al nivel de almacenamiento (no solo de presentación): ni
siquiera una consulta directa a la base de datos expone el valor completo. Es una
extensión directa del Principio IV de la constitución (Privacidad por Diseño) más allá
de los logs.

**Alternatives considered**:
- Enmascarar solo al servir la respuesta de `GET .../historial` (guardar el valor
  completo en BD): rechazada — un acceso directo a la base de datos (backup, réplica,
  consulta ad-hoc) expondría el valor sensible sin pasar por la API.

## 6. Estructura y almacenamiento del diff (`persona_historial.cambios`)

**Decision**: Columna `cambios` de tipo `JSONB`, mapeada en Hibernate 6 con
`@JdbcTypeCode(SqlTypes.JSON)` sobre un `String` que contiene el JSON ya serializado
(Jackson) de `List<CampoCambiado>` (`record CampoCambiado(String campo, String
valorAnterior, String valorNuevo)`). Se usa el mismo formato uniforme para las cuatro
operaciones:

- `CREACION`: cada campo persistido aparece con `valorAnterior = null`.
- `MODIFICACION`: solo los campos que realmente cambiaron (incluidos los de `direccion`,
  con el prefijo `direccion.`, p. ej. `direccion.calle` — FR-008).
- `ELIMINACION` / `RESTAURACION`: una entrada `{"campo":"activo","valorAnterior":...,
  "valorNuevo":...}`.

**Rationale**: Un solo formato para las cuatro operaciones evita casos especiales
(Principio IV) y simplifica tanto la escritura (`HistorialDiffService`) como la lectura
(`GET .../historial` no necesita ramas distintas por tipo de operación). El soporte
nativo de `JSONB` de Hibernate 6.5.x evita añadir una dependencia externa
(`hibernate-types` u otra) solo para este propósito.

**Alternatives considered**:
- Columnas separadas por campo posible (`curp_anterior`, `curp_nuevo`, ...): rechazada
  — acopla el esquema de `persona_historial` a la lista actual de campos de `persona`/
  `direccion`, rompiendo con cualquier campo nuevo que se agregue en el futuro
  (violaría el propio Principio V al forzar migraciones no relacionadas cada vez que
  cambie el modelo de `persona`).
- Dependencia `hibernate-types` (Vlad Mihalcea) para el mapeo de JSON: rechazada —
  innecesaria desde Hibernate 6.3+, que soporta `JSONB` de forma nativa.

## 7. Índice de `persona_historial`

**Decision**: `CREATE INDEX ix_persona_historial_persona_fecha ON persona_historial
(persona_id, fecha DESC);` — tal como pidió el solicitante, con orden `DESC` explícito
en `fecha` para servir directamente `GET .../historial` (FR-011: más reciente primero)
sin un sort adicional en memoria.

**Rationale**: Cubre exactamente el único patrón de acceso de esta tabla (listar el
historial de una persona, del más reciente al más antiguo).

## 8. Nueva operación: restaurar una persona eliminada lógicamente

**Decision**: `PersonaService.restaurar(UUID id)` (transaccional, como el resto del
`service`): busca la persona **sin** filtrar por `activo` (nuevo método de repositorio
`findById` simple, ya heredado de `JpaRepository`); si no existe, `404
PERSONA_NO_ENCONTRADA`; si `activo = true`, `409 PERSONA_YA_ACTIVA` (código de error
nuevo); si `activo = false`, valida que `correo`/`curp` no estén tomados por otra persona
activa (reutilizando `existsByCorreoAndActivoTrue`/`existsByCurpAndActivoTrue`, ya
existentes) — si lo están, el mismo `409 PERSONA_CORREO_DUPLICADO`/
`PERSONA_CURP_DUPLICADO` que usan creación/actualización (FR-014); si pasa, marca
`activo = true` y registra la entrada de historial `RESTAURACION`. Expuesto como
`PATCH /api/personas/{id}/restaurar`, `@PreAuthorize("hasRole('ADMIN')")` (spec
Clarifications).

**Rationale**: Reutiliza las validaciones de unicidad ya existentes en vez de duplicar
lógica (Principio IV), y sigue el mismo patrón de autorización por rol ya establecido
en `002` para operaciones sensibles.

## Resumen de resolución de NEEDS CLARIFICATION

No quedaban marcadores `NEEDS CLARIFICATION` pendientes en el Technical Context (la
única ambigüedad de la especificación — qué rol puede restaurar — ya se resolvió en
`/speckit-clarify`/`/speckit-specify` antes de este plan). Las secciones anteriores
documentan las decisiones de diseño derivadas necesarias para implementar el mecanismo
solicitado cumpliendo el spec y la constitución del proyecto.

# Phase 0 Research: Catálogo de Profesiones y Asignación a Personas

## 1. Unicidad de `profesion.nombre` insensible a mayúsculas y acentos

**Decision**: Índice único funcional `unaccent(lower(nombre))` sobre la
columna `nombre` de `profesion`, reutilizando la extensión `unaccent` ya
creada por `005-busqueda-avanzada-personas` (`V5__unaccent_busqueda_personas.sql`,
vía la función `unaccent_immutable` ahí definida — `IMMUTABLE`, requisito
para poder indexarla). La migración `V7` declara `CREATE EXTENSION IF NOT
EXISTS unaccent` de todas formas, como precaución idempotente por si algún
entorno aplicara esta migración sin haber corrido antes `V5` (aditivo, sin
efecto si ya existe).

**Rationale**: Mismo mecanismo ya validado y en producción para
`idx_persona_nombre_completo_unaccent`; evita introducir una segunda forma
de resolver el mismo problema (Principio I).

**Alternatives considered**:
- Trigger de normalización a una columna `nombre_normalizado` aparte:
  rechazada por añadir una columna redundante y lógica de sincronización
  manual cuando un índice de expresión ya resuelve el problema sin
  duplicar datos.
- Comparar sin `unaccent` (solo `lower`): rechazada porque el propio alcance
  pide explícitamente tratar "Mecánico" y "mecanico" como el mismo nombre.

## 2. Reutilizar `unaccent_immutable` vs. declarar una función propia

**Decision**: Reutilizar la función `unaccent_immutable(text)` ya creada por
`V5` (misma base de datos, mismo esquema `public`); `V7` no la vuelve a
declarar.

**Rationale**: Es la misma extensión y el mismo problema (comparación
insensible a acentos) ya resuelto una vez; declarar una segunda función
idéntica violaría el Principio I sin ningún beneficio.

**Alternatives considered**: Declarar `profesion_unaccent_immutable` propia
— rechazada por duplicación innecesaria; ambas migraciones conviven en el
mismo esquema, por lo que la función de `V5` ya está disponible para `V7`.

## 3. Unicidad de `persona_profesion` entre asignaciones activas

**Decision**: Índice único parcial `UNIQUE (persona_id, profesion_id) WHERE
activo = true`, mismo patrón que `ux_persona_correo_activo` (`V1`) y
`ux_persona_curp_activo` (reemplazado luego por unicidad global en `V4`,
pero el patrón de índice parcial en sí sigue vigente para `correo`).

**Rationale**: Es exactamente la semántica pedida (FR-013): una persona no
puede tener la misma profesión activa dos veces, pero sí puede acumular
episodios retirados de la misma profesión — el índice parcial permite
múltiples filas `activo = false` para el mismo par pero como mucho una
`activo = true`.

**Alternatives considered**: Restricción única sobre `(persona_id,
profesion_id)` sin filtro — rechazada explícitamente: impediría el episodio
histórico acumulado que pide FR-013 (reasignar tras retirar).

## 4. Reasignación tras retiro: fila nueva, no reactivación

**Decision** (`/speckit-clarify`, sesión 2026-07-08): reasignar a una
persona una profesión previamente retirada crea una fila **nueva** en
`persona_profesion`, con su propia `fecha_desde`/`cedula`; la fila retirada
anterior no se modifica.

**Rationale**: Coincide con el patrón dominante ya usado en el proyecto para
registros de episodios (`refresh_token`, `persona_historial`,
`catalogo_importacion`: siempre insertan una fila nueva por evento en vez de
reactivar una existente), y permite que cada episodio tenga su propio dato
de cédula/fecha si cambiaron entre uno y otro.

**Alternatives considered**: Reactivar la misma fila (como `persona.activo`
en la restauración) — rechazada por decisión explícita del usuario en la
sesión de clarify; además perdería la cédula/fecha del episodio anterior al
sobreescribirse.

## 5. Auditoría de `Profesion` y `PersonaProfesion`

**Decision**: `Profesion` extiende `Auditable` (columnas `creado_por`/
`actualizado_por`/`created_at`/`updated_at`) sin tabla de historial propia
— mismo patrón que `CatalogoImportacion` (`006`), que tampoco tiene un
historial dedicado. `PersonaProfesion` también extiende `Auditable`, y
además cada asignación/retiro se registra como una entrada `MODIFICACION`
en la tabla `persona_historial` ya existente (vía un método nuevo en
`HistorialDiffService`), ya que está atada a una `persona_id` concreta —
igual que los cambios de dirección de una persona ya se registran ahí.

**Rationale**: `persona_historial.persona_id` es `NOT NULL` — solo tiene
sentido para eventos atados a una persona. `Profesion` (catálogo) no lo
está, por lo que su auditoría "quién/cuándo" se resuelve con las columnas
de `Auditable`, consistente con cómo ya se audita `CatalogoImportacion`.

**Alternatives considered**: Tabla de historial propia para `profesion` —
rechazada por sobre-ingeniería: el catálogo es pequeño y administrado
manualmente por ADMIN; las columnas de `Auditable` ya satisfacen "quién y
cuándo" (FR-024) sin necesitar un diff de campos versionado.

## 6. Validación de duplicados en el service vs. índice de base de datos

**Decision**: El service normaliza el nombre (`unaccent` + `lower`, vía una
consulta que aplica la misma función `unaccent_immutable` antes de
comparar) para decidir el 409 de negocio (`PROFESION_NOMBRE_DUPLICADO` o
`PROFESION_NOMBRE_DESACTIVADA`), y el índice único de la BD actúa como
respaldo final ante condiciones de carrera — mismo principio de doble capa
ya usado para `persona.correo`/`persona.curp`.

**Rationale**: Evita que el chequeo aplicativo y el índice alguna vez
discrepen (la misma normalización en ambos lados es la garantía), y sigue
el patrón de "chequeo de service + constraint de BD como red de seguridad"
ya establecido en el proyecto.

**Alternatives considered**: Confiar únicamente en la excepción de
violación de constraint (capturar `DataIntegrityViolationException`) sin
chequeo previo — rechazada porque el mensaje de negocio (FR-005: indicar
que puede reactivarse) necesita distinguir "ya existe activa" de "ya existe
desactivada", algo que una violación de constraint genérica no puede
expresar por sí sola sin una consulta previa de todas formas.

## 7. Estructura de paquetes y ubicación de los sub-recursos de asignación

**Decision**: Nuevo dominio `mx.personas.api.profesion` (catálogo +
directorio); los 3 endpoints `/api/personas/{id}/profesiones` se agregan al
`PersonaController` ya existente, delegando a un nuevo
`PersonaProfesionService` (ubicado en el dominio `profesion`, por depender
fuertemente de la entidad `Profesion`).

**Rationale**: Igual que `historial`/`restaurar` ya viven en
`PersonaController` aunque `PersonaHistorial` tiene su propio archivo en
`persona.model`, el sub-recurso "profesiones de esta persona" pertenece a la
URL de persona, no a la de profesión — pero su lógica de negocio (que
depende del catálogo `Profesion`) vive en el dominio `profesion`.

**Alternatives considered**: Controller separado
`PersonaProfesionController` bajo `/api/personas/{id}/profesiones` —
rechazado por fragmentar innecesariamente el ya reducido `PersonaController`
en dos controllers para el mismo prefijo de URL, sin necesidad real dado el
bajo número de endpoints (3).

## 8. Endpoints de acción (desactivar/reactivar/retirar)

**Decision**: `PATCH /api/profesiones/{id}/desactivar`, `PATCH
/api/profesiones/{id}/reactivar`, `PATCH
/api/personas/{id}/profesiones/{asignacionId}/retirar` — mismo patrón ya
usado en `UsuarioController` (`PATCH /api/usuarios/{id}/desactivar`).

**Rationale**: Son transiciones de estado explícitas, no actualizaciones de
campo genéricas; el patrón `PATCH .../accion` ya está establecido en el
proyecto para este tipo de operación.

**Alternatives considered**: Un único `PATCH /api/profesiones/{id}` que
acepte tanto `descripcion` como `activo` en el mismo cuerpo (como
`PersonaUpdateDTO`) — rechazada porque el propio alcance distingue
"editar descripción" (ADMIN, campo de datos) de "desactivar/reactivar"
(ADMIN, transición de estado con sus propias reglas de negocio — bloqueo de
asignaciones nuevas), y mezclar ambos en un único DTO oscurecería esa
distinción y complicaría la validación de "no se puede reactivar y editar a
la vez de forma ambigua".

## 9. DTO del directorio por profesión

**Decision**: `PersonaDirectorioDTO(id, nombreCompleto, fechaDesde,
cedula)` — nunca `PersonaResponseDTO` ni `PersonaResumenDTO` completos.

**Rationale**: El alcance pide explícitamente "no todos los datos
personales"; further, Principio IV (Privacidad por Diseño) favorece la
mínima exposición necesaria. `nombreCompleto` se resuelve concatenando
`nombres + apellidos` en el mapper (no se expone correo/teléfono/CURP/RFC/
dirección).

**Alternatives considered**: Reutilizar `PersonaResumenDTO` filtrando campos
en el cliente — rechazada: expondría datos personales completos en la
respuesta del servidor, violando el requisito explícito del alcance y el
Principio IV.

## 10. Testing de unicidad insensible a acentos

**Decision**: Tests de repositorio con Testcontainers/PostgreSQL real (no
H2), mismo criterio que `005-busqueda-avanzada-personas` (`research.md`
§9): `unaccent` no existe en H2.

**Rationale**: Es la misma limitación ya documentada y resuelta para la
búsqueda de personas; se reutiliza el mismo patrón de `AbstractIntegrationTest`
(contenedor Postgres singleton).

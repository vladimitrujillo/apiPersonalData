# Research: Restaurar Personas y Unicidad de CURP Global

## 1. Estado real de la unicidad de `correo`/`curp` (corrección de premisa)

**Hallazgo**: `V1__create_schema.sql` (única migración existente antes de este feature)
define:

```sql
CREATE UNIQUE INDEX ux_persona_correo_activo ON persona (correo) WHERE activo = true;
CREATE UNIQUE INDEX ux_persona_curp_activo ON persona (curp) WHERE activo = true;
```

Es decir, **ambas** columnas son hoy únicas solo entre registros activos. No existe
ningún `uq_persona_correo` (constraint global) que reemplazar, y ningún `@Column` de
`Persona` declara `unique = true` (la unicidad siempre se implementó en Flyway —
`specs/001-personas-codigos-postales/research.md` §2 — nunca en JPA).

**Decision**: Confirmado con el usuario durante la sesión de planeación: se corrige el
entendimiento y se procede así — `correo` no requiere ninguna migración (ya cumple D3
tal cual está); `curp` es la única columna que migra, de índice parcial a `UNIQUE`
global (D2). Esto también resuelve el TODO que la propia constitución del proyecto
(v2.0.0) había dejado pendiente sobre este mismo hallazgo.

**Rationale**: Migrar algo que ya cumple la regla deseada (`correo`) sería trabajo sin
efecto (viola Principio IV); no migrar lo que realmente necesita cambiar (`curp`)
dejaría el feature sin su cambio central (FR-002 de la spec).

**Alternatives considered**:
- Renombrar el índice de `correo` de `ux_persona_correo_activo` a
  `uq_persona_correo_activo` por consistencia de nomenclatura con `curp`: se descarta
  como innecesario — no cambia comportamiento, y tocar un objeto que funciona
  correctamente sin necesidad no aporta valor (Principio IV). Si el equipo quiere
  homogeneizar el prefijo `ux_`/`uq_` en el futuro, es un cambio cosmético
  independiente, no parte de este feature.

## 2. Migración: `curp` de índice parcial a `UNIQUE` global

**Decision**: `V4__globalizar_unicidad_curp.sql`, con tres pasos en orden:

```sql
-- 1. Precondición: fallar explícitamente si existen CURP duplicados entre CUALQUIER
--    combinación de registros (activos y/o inactivos) — no solo entre activos.
DO $$
DECLARE
    duplicados INT;
BEGIN
    SELECT COUNT(*) INTO duplicados FROM (
        SELECT curp FROM persona GROUP BY curp HAVING COUNT(*) > 1
    ) AS c;
    IF duplicados > 0 THEN
        RAISE EXCEPTION
            'No se puede aplicar UNIQUE global sobre persona.curp: existen % CURP duplicados (entre activos y/o eliminados). Resolver manualmente antes de reintentar esta migración.',
            duplicados;
    END IF;
END $$;

-- 2. Retirar el índice parcial (solo-activos)
DROP INDEX ux_persona_curp_activo;

-- 3. Restricción única global
ALTER TABLE persona ADD CONSTRAINT uq_persona_curp UNIQUE (curp);
```

**Rationale**: El bloque `DO $$ ... END $$` con `RAISE EXCEPTION` aborta la transacción
de la migración (Flyway envuelve cada migración de Postgres en una transacción por
defecto), cumpliendo exactamente el requisito del usuario: "si existieran, la migración
falla y se resuelve a mano" — sin necesidad de una migración Java ni de un script
externo, manteniendo el estilo 100% SQL plano ya establecido (Principio I). La
verificación cubre deliberadamente **todos** los registros (no `WHERE activo = true`),
porque el objetivo final es unicidad global absoluta: dos registros con la misma CURP,
aunque uno esté eliminado, ya serían un duplicado inadmisible bajo D2.

**Alternatives considered**:
- Confiar únicamente en que `ALTER TABLE ... ADD CONSTRAINT UNIQUE` falle por sí mismo
  si hay duplicados (sin el bloque `DO` previo): rechazada — el error nativo de
  Postgres (`23505`) no explica la causa de negocio ("hay CURP duplicados, resolver a
  mano") tan claramente como un mensaje explícito, y el usuario pidió documentar la
  precondición explícitamente.
- Migración Java (`JavaMigration`) que resuelva duplicados automáticamente (p. ej.
  desactivando todos menos el más reciente): rechazada explícitamente por el usuario y
  por la spec — la resolución de duplicados preexistentes es manual, no automática (no
  hay regla de negocio para decidir cuál de los duplicados "gana").

## 3. Detección del conflicto de CURP en el `service` (activo vs. eliminado)

**Decision**: Nuevo método `PersonaRepository.findByCurp(String curp)` (sin filtro de
`activo` — la CURP es global, a lo sumo una fila puede coincidir). En `crear()` y en
`actualizar()` (cuando cambia la CURP), el `service`:

1. Busca `findByCurp(curp)`.
2. Si no hay resultado (o el resultado es la misma persona que se está actualizando):
   procede sin conflicto.
3. Si hay resultado y `activo = true`: `DuplicateFieldException(PERSONA_CURP_DUPLICADO,
   ...)` (409, sin cambio de comportamiento respecto a hoy).
4. Si hay resultado y `activo = false`: nueva excepción mapeada a
   `PERSONA_CURP_ELIMINADA` (409), con mensaje accionable y `detalles` incluyendo el
   `id` del registro eliminado — nunca su nombre, correo, teléfono ni otros datos
   (FR-004).

**Rationale**: Reutiliza el patrón ya existente (`DuplicateFieldException` +
`ErrorCode`) agregando solo la rama de decisión y un código nuevo, en vez de introducir
un mecanismo de manejo de errores paralelo (Principio I/IV). La restricción `UNIQUE`
global de la BD (research.md §2) sigue siendo la garantía atómica ante condiciones de
carrera, igual que en `specs/001.../research.md` §2 para el caso de `correo`.

**Alternatives considered**:
- Un solo código de error `PERSONA_CURP_DUPLICADO` para ambos casos (activo/eliminado),
  diferenciando solo por el texto del `mensaje`: rechazada — la spec (FR-004) pide un
  409 distinguible y accionable; un `codigo` propio permite que un cliente/UI reaccione
  de forma distinta (p. ej. mostrar un botón "solicitar restauración a un ADMIN") sin
  parsear el mensaje.

## 4. Validación de correo al restaurar (misma transacción)

**Decision**: `PersonaService.restaurar(UUID id)` (método `@Transactional`, igual que el
resto de la clase) reutiliza el método ya existente
`PersonaRepository.existsByCorreoAndActivoTrue(correo)` — sin `AndIdNot`, porque la
persona a restaurar está actualmente inactiva y por tanto nunca aparece en ese chequeo a
menos que ya se haya reactivado — dentro del mismo método que marca `activo = true`, de
modo que un fallo de esa validación no deja a la persona a medio reactivar.

**Rationale**: Reutiliza un método de repositorio que ya existe (desde `specs/001`) sin
necesidad de agregar uno nuevo, y la ejecución dentro de la misma transacción que la
reactivación es automática al ser el mismo método `@Transactional` (no requiere
configuración adicional).

## 5. Endpoint de restauración y vista de eliminados

**Decision**: `POST /api/personas/{id}/restaurar` (no `PATCH` — corrige el verbo
asumido por `003`, ver plan.md § Reconciliación) y `GET /api/personas/eliminadas`,
ambos `@PreAuthorize("hasRole('ADMIN')")`. El segundo reutiliza la misma forma de
paginación que `GET /api/personas` (página/tamaño), filtrando por `activo = false` en
vez de `activo = true` (nuevo método de repositorio `findByActivoFalse(Pageable)`,
simétrico al ya existente `buscarActivas(...)`).

**Rationale**: Endpoint separado (no un parámetro sobre el listado general) según la
decisión tomada en `/speckit-clarify` de este feature — mantiene `GET /api/personas`
sin ramas de comportamiento por rol.

## 6. Integración con el historial de `003`

**Decision**: `restaurar()` llama al mismo `HistorialDiffService`/inserción de
`PersonaHistorial` que `003` introduce para `crear`/`actualizar`/`eliminar`, con
`operacion = RESTAURACION`, dentro de la misma transacción (FR-011). Este plan no
redefine ese mecanismo, solo lo consume — ver `specs/003-auditoria-personas/research.md`
§8 (que ya había diseñado esta misma integración, aunque con supuestos de conflicto
distintos, ya corregidos aquí — ver plan.md § Reconciliación).

## Resumen de resolución de NEEDS CLARIFICATION

No quedaban marcadores `NEEDS CLARIFICATION` pendientes en el Technical Context (la
única ambigüedad de la especificación — el mecanismo de la vista de eliminados — ya se
resolvió en `/speckit-specify` antes de este plan). La corrección de premisa sobre el
estado real de `correo`/`curp` (§1) fue confirmada explícitamente por el usuario durante
esta sesión de planeación.

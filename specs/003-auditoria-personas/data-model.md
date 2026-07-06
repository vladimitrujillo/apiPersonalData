# Data Model: Auditoría y Historial de Cambios en Personas

Basado en el spec (Key Entities) y en las decisiones de `research.md`. Extiende el
modelo de `specs/001-personas-codigos-postales/data-model.md` (`persona`, `direccion`)
y depende del modelo de `specs/002-autenticacion-autorizacion/data-model.md`
(`usuario`). Los tipos de columna son indicativos para PostgreSQL/Flyway; se detallan
en la migración durante la implementación (`tasks.md`), no aquí.

## persona (columnas nuevas)

| Campo | Tipo | Reglas |
|---|---|---|
| `creado_por` | UUID (FK → usuario, **nullable**) | Poblado automáticamente por JPA Auditing (`@CreatedBy`) al crear; `NULL` para filas creadas antes de este feature (research.md §3) |
| `actualizado_por` | UUID (FK → usuario, **nullable**) | Poblado automáticamente por JPA Auditing (`@LastModifiedBy`) en cada `UPDATE`, incluida la eliminación lógica y la restauración (ambas son un `UPDATE` de `persona`) |

`created_at`/`updated_at` (ya existentes desde `specs/001`) no cambian de columna; pasan
a ser gestionadas por `@CreatedDate`/`@LastModifiedDate` en vez de asignación manual
(research.md §2), sin impacto en su tipo ni en su `NOT NULL`.

## direccion (columnas nuevas)

Mismas dos columnas nuevas (`creado_por`, `actualizado_por`), mismas reglas, mismo
mecanismo — `direccion` extiende el mismo `@MappedSuperclass Auditable`.

## persona_historial (tabla nueva)

Registro inmutable de cada operación sobre una persona (spec FR-005 a FR-011).

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | UUID (PK) | Generado por el sistema |
| `persona_id` | UUID (FK → persona) | Requerido; **no** se borra en cascada si la persona se elimina lógicamente (la persona nunca se borra físicamente) |
| `usuario_id` | UUID (FK → usuario) | Requerido; autor de la operación. Siempre presente porque toda escritura pasa por un endpoint autenticado (`002`) |
| `operacion` | varchar(20), `CHECK IN ('CREACION','MODIFICACION','ELIMINACION','RESTAURACION')` | Requerido |
| `fecha` | timestamptz, `NOT NULL DEFAULT now()` | Momento de la operación |
| `cambios` | `JSONB NOT NULL` | Array de `{campo, valorAnterior, valorNuevo}` (research.md §6); CURP/RFC/teléfono siempre enmascarados dentro de estos valores (research.md §5) |

**Índice**: `CREATE INDEX ix_persona_historial_persona_fecha ON persona_historial
(persona_id, fecha DESC);` (research.md §7) — soporta directamente `GET
/api/personas/{id}/historial` ordenado del más reciente al más antiguo.

**Inmutabilidad**: no existe ningún endpoint ni método de `service`/`repository` que
actualice o borre una fila de `persona_historial` ya insertada (FR-009). Solo `INSERT`.

**Transacción**: cada fila de `persona_historial` se inserta dentro de la misma
transacción de negocio (`@Transactional`, ya presente a nivel de clase en
`PersonaService`) que la operación que la origina; si el `INSERT` falla, toda la
transacción se revierte, incluido el cambio en `persona`/`direccion` (FR-010).

## Resumen de relaciones

```text
usuario 1---N persona (vía creado_por / actualizado_por, ambas nullable)
usuario 1---N direccion (idem)
usuario 1---N persona_historial (usuario_id, autor de cada operación)
persona 1---N persona_historial (persona_id)
```

## Contraste con el modelo de auditoría (qué campo vive dónde)

| Pregunta | Dónde se responde |
|---|---|
| ¿Quién creó/modificó por última vez esta persona, y cuándo? | `persona.creado_por`/`creado_en` (=`created_at`)/`actualizado_por`/`actualizado_en` (=`updated_at`) — expuesto en `GET /api/personas/{id}` (FR-003), **no** en el listado (FR-004) |
| ¿Qué pasó exactamente con esta persona a lo largo del tiempo? | `persona_historial`, expuesto solo en `GET /api/personas/{id}/historial`, solo ADMIN (FR-011, FR-012) |

Esta tabla documenta la distinción explícita entre los dos mecanismos que pide la spec:
auditoría básica de "estado actual" (dos campos) vs. historial completo de eventos (una
tabla append-only).

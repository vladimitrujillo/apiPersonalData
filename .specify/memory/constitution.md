<!--
Sync Impact Report
==================
Version change: 1.0.0 → 2.0.0 (MAJOR: core principle set redefined/replaced)
Modified principles:
  - "I. API-First" → replaced by "II. No Romper el Contrato" (narrower: contract
    stability of existing /v3/api-docs endpoints, not a mandate that all logic be
    API-exposed)
  - "II. Test-First (NON-NEGOTIABLE)" → "III. Test-First con Suite Siempre Verde"
    (kept as non-negotiable; added explicit "existing suite must stay green" gate)
  - "III. Privacidad por Diseño" → "IV. Privacidad por Diseño" (renumbered, content
    unchanged)
Added principles:
  - I. Respetar lo Existente (convention-first for a pre-existing, functional codebase)
  - V. Migraciones Solo Aditivas y Versionadas
  - VI. Identidad vs Contacto (usuario/persona/CURP uniqueness model)
Removed as standalone principles (demoted to "Restricciones Adicionales" where content
is still valid and not contradicted by the new set, to avoid silently discarding
still-useful guidance):
  - "IV. Simplicidad" → demoted to a restriction bullet
  - "V. Consistencia en Manejo de Errores" → demoted to a restriction bullet
    (now framed as an instance of Principle I: follow the existing
    @RestControllerAdvice / GlobalExceptionHandler format)
  - "VI. Catálogos de Referencia Locales e Idempotentes" → demoted to a restriction
    bullet (SEPOMEX loader idempotency is existing, functioning behavior; still
    required, just no longer a top-level principle per the user-specified 6)
Removed sections: None
Templates requiring updates:
  - .specify/templates/plan-template.md ✅ compatible as-is (Constitution Check gate
    is dynamic; no hard-coded principle list to update)
  - .specify/templates/spec-template.md ✅ compatible as-is (no constitution-specific
    references)
  - .specify/templates/tasks-template.md ✅ compatible as-is (no hard-coded principle
    list; task categories still map to Principles III/IV/V)
Follow-up TODOs:
  - KNOWN SCHEMA CONFLICT: `src/main/resources/db/migration/V1__create_schema.sql`
    defines `ux_persona_curp_activo` as a PARTIAL unique index (CURP unique only
    among `activo = true` rows). Principle VI now requires CURP to have global,
    unconditional uniqueness (lifetime identity). V1 is already applied and per
    Principle V MUST NOT be edited. A future additive migration is needed to
    introduce a global unique constraint on CURP (and a plan for reconciling any
    existing duplicate CURPs across active/inactive rows before applying it).
    Not resolved by this amendment — flagged for the next feature that touches
    `persona`/CURP or introduces the `usuario` (credentials) table.
  - The `usuario` (access credentials) table referenced by Principle VI does not
    yet exist in the schema; Principle VI describes the intended invariant for
    when it is introduced, not a currently-enforced constraint.
-->

# apiPersonalData Constitution

## Core Principles

### I. Respetar lo Existente

Toda feature nueva DEBE seguir las convenciones ya presentes en el código: la
estructura de paquetes por dominio (`mx.personas.api.<dominio>.{controller,dto,
mapper,model,repository,service}`), el flujo de capas controller → service →
repository, el mapeo de entidades a DTOs vía MapStruct, el formato de error
centralizado en `GlobalExceptionHandler` (`@RestControllerAdvice`), y el estilo y
ubicación de las migraciones Flyway (`src/main/resources/db/migration/V{n}__descripcion.sql`).
Ante cualquier duda de diseño o estilo, se DEBE leer el código existente análogo
antes de introducir un patrón, dependencia o convención nueva.

**Rationale**: Este es un backend ya funcional en operación, no un proyecto en cero.
Introducir patrones paralelos o inconsistentes incrementa el costo de mantenimiento
y la probabilidad de errores, y dificulta que futuras features (o auditorías de
privacidad) razonen sobre el código con un modelo mental único.

### II. No Romper el Contrato

Los endpoints ya existentes y documentados en `/v3/api-docs` NO deben cambiar de
comportamiento, forma de request/response, ni códigos de estado, salvo que la
especificación de la feature lo pida explícitamente. Cambios aditivos (endpoints
nuevos, campos opcionales nuevos, nuevos valores de enum que no rompan a
consumidores existentes) están permitidos sin aprobación adicional. Cualquier
breaking change (renombrar o eliminar un campo, cambiar un tipo, cambiar la
semántica de un status code, eliminar un endpoint) requiere aprobación explícita
documentada en el plan de la feature antes de implementarse.

**Rationale**: Consumidores internos y externos dependen del contrato actual;
romperlo sin acuerdo explícito genera incidentes en producción que no son
detectables por la suite de tests interna.

### III. Test-First con Suite Siempre Verde

No se escribe código de implementación sin que existan tests previos que fallen
primero (ciclo Red-Green-Refactor). Toda funcionalidad nueva o cambio de
comportamiento DEBE tener tests escritos y validados antes de iniciar la
implementación correspondiente. Además, la suite de tests existente DEBE
permanecer en verde en todo momento: ningún cambio se integra si rompe un test
que pasaba antes del cambio.

**Rationale**: Previene regresiones sobre un sistema ya en producción y obliga a
definir el comportamiento esperado antes de escribir código, lo cual es
especialmente crítico al manipular datos personales e identidad.

### IV. Privacidad por Diseño

Los datos personales NUNCA deben registrarse (loguearse) en texto plano, en
ningún nivel de log (debug, info, warning, error). Todo campo sensible (p. ej.
CURP, RFC, teléfono, correo electrónico, dirección) DEBE validarse y sanitizarse
en la capa de entrada antes de procesarse o persistirse. Los logs y los mensajes
de error DEBEN enmascarar u omitir dichos valores.

**Rationale**: El proyecto maneja datos personales; la fuga vía logs es una de
las causas más comunes de incidentes de privacidad y puede tener implicaciones
legales.

### V. Migraciones Solo Aditivas y Versionadas

Ninguna migración Flyway ya aplicada (p. ej. `V1__create_schema.sql`) se edita
jamás, sin excepción. Todo cambio de esquema se implementa como una nueva
migración versionada (`V{n+1}__descripcion.sql`). Las migraciones deben ser
aditivas por defecto (agregar tablas, columnas, índices). Cuando un cambio
requiera modificar o eliminar algo existente, debe hacerse mediante una
secuencia de migraciones retrocompatible (p. ej. columna nueva nullable →
backfill → constraint), documentando explícitamente el porqué en la migración
o en el plan de la feature.

**Rationale**: Editar una migración ya aplicada rompe los checksums de Flyway y
genera drift entre entornos (local, CI, producción) que ya la ejecutaron. Un
historial de migraciones inmutable es la única fuente confiable del estado del
esquema en cualquier entorno.

### VI. Identidad vs Contacto

El sistema distingue explícitamente entre identidad y datos de contacto:

- Las credenciales de acceso (tabla `usuario`) tienen unicidad global y NUNCA se
  liberan ni reutilizan, ni siquiera cuando el usuario se desactiva.
- Los datos de contacto de las personas registradas (`persona.correo`) tienen
  unicidad solo entre registros activos; al desactivarse una persona, su correo
  puede reutilizarse por otro registro activo.
- La CURP es identidad vitalicia con unicidad global absoluta, sin excepción por
  estado activo/inactivo.
- Los datos de contacto de personas (correo, teléfono) NUNCA se usan como
  credencial de autenticación.

**Rationale**: Separar identidad (inmutable, única de por vida, con implicaciones
legales) de contacto (mutable, liberable) evita fraude por reutilización de
identidad y permite, al mismo tiempo, que la información de contacto evolucione
con normalidad sin comprometer la trazabilidad de una persona a lo largo del
tiempo.

## Restricciones Adicionales

- Simplicidad: el proyecto se mantiene como una sola aplicación desplegable; no
  se introducen microservicios, colas de mensajes ni capas de abstracción
  adicionales salvo necesidad concreta y demostrada (YAGNI por defecto).
- El formato de error consistente ya definido en `GlobalExceptionHandler` (ver
  Principio I) es de uso obligatorio para toda respuesta de error nueva; no se
  introducen formatos de error ad-hoc por endpoint.
- Los procesos de carga de catálogos de referencia externos (p. ej. SEPOMEX)
  DEBEN seguir siendo idempotentes: ejecutarlos múltiples veces con los mismos
  datos de origen DEBE producir el mismo estado final, sin duplicados.
- Toda comunicación externa de la API DEBE usar HTTPS/TLS.
- Los campos sensibles DEBEN cifrarse en reposo cuando la naturaleza del dato lo
  requiera (p. ej. identificadores oficiales).
- El acceso a datos personales DEBE estar sujeto a autenticación y autorización
  explícitas; no existen endpoints de lectura de datos personales sin control de
  acceso.

## Flujo de Desarrollo

- Ante cualquier decisión de diseño o estilo no cubierta explícitamente aquí, se
  lee el código existente análogo antes de decidir (Principio I).
- Todo cambio a un endpoint existente se valida contra `/v3/api-docs`: si no es
  aditivo, requiere aprobación explícita documentada en el plan antes de
  implementarse (Principio II).
- Todo cambio de código requiere tests escritos y en estado fallido antes de la
  implementación, y la suite completa DEBE quedar en verde antes de cerrar la
  tarea o el PR (Principio III).
- Toda revisión de código verifica que no se introduzcan logs con datos
  personales en texto plano (Principio IV).
- Todo cambio de esquema se implementa como una migración Flyway nueva; nunca se
  edita una ya aplicada (Principio V).
- Toda feature que toque `usuario`, `persona.correo` o `persona.curp` DEBE
  verificar explícitamente que respeta el modelo de unicidad de identidad vs.
  contacto (Principio VI), incluyendo, si corresponde, prueba de que el CURP
  mantiene unicidad global incluso entre registros inactivos.

## Governance

Esta constitución prevalece sobre cualquier otra práctica, guía o convención del
proyecto. En caso de conflicto entre esta constitución y otra documentación
(README, comentarios de código, decisiones ad-hoc), la constitución tiene
prioridad.

**Enmiendas**: Cualquier cambio a esta constitución (adición, modificación o
eliminación de un principio) requiere: (1) documentar la propuesta y su
justificación, (2) actualizar este archivo con el nuevo número de versión
conforme al versionado semántico descrito abajo, y (3) revisar los templates
dependientes (`plan-template.md`, `spec-template.md`, `tasks-template.md`) para
mantener la consistencia.

**Versionado**: MAJOR para eliminaciones o redefiniciones incompatibles de
principios; MINOR para la adición de nuevos principios o expansión material de
una guía existente; PATCH para aclaraciones o correcciones de redacción sin
cambio semántico.

**Cumplimiento**: Todo PR o revisión de código DEBE verificar el cumplimiento de
los principios anteriores. Cualquier desviación (p. ej. una excepción al
principio de Simplicidad para un caso concreto) DEBE justificarse explícitamente
en la descripción del PR, incluyendo la alternativa más simple considerada y por
qué fue rechazada.

**Versión**: 2.0.0 | **Ratificada**: 2026-07-02 | **Última Enmienda**: 2026-07-06

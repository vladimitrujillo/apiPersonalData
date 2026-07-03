<!--
Sync Impact Report
==================
Version change: [TEMPLATE] → 1.0.0 (initial ratification)
Modified principles: N/A (first concrete version; template placeholders replaced)
Added sections:
  - I. API-First
  - II. Test-First (NON-NEGOTIABLE)
  - III. Privacidad por Diseño
  - IV. Simplicidad
  - V. Consistencia en Manejo de Errores
  - VI. Catálogos de Referencia Locales e Idempotentes
  - Restricciones Adicionales (nueva sección secundaria)
  - Flujo de Desarrollo (nueva sección secundaria)
Removed sections: None (template placeholders only)
Templates requiring updates:
  - .specify/templates/plan-template.md ✅ compatible as-is (Constitution Check gate is dynamic; no hard-coded principle list to update)
  - .specify/templates/spec-template.md ✅ compatible as-is (no constitution-specific references)
  - .specify/templates/tasks-template.md ✅ compatible as-is (already includes error handling/logging and optional test-first tasks matching Principles II, III, V)
Follow-up TODOs: None
-->

# apiPersonalData Constitution

## Core Principles

### I. API-First

Toda funcionalidad del sistema DEBE exponerse a través de una API REST, documentada
con OpenAPI (Swagger). No se permite lógica de negocio accesible únicamente por vías
no documentadas (scripts internos, acceso directo a base de datos, etc.). Todo endpoint
nuevo o modificado DEBE actualizar la especificación OpenAPI correspondiente en el mismo
cambio antes de considerarse completo.

**Rationale**: Garantiza que consumidores internos y externos cuenten con un contrato
claro y verificable, y evita la existencia de API "fantasma" no documentada.

### II. Test-First (NON-NEGOTIABLE)

No se escribe código de implementación sin que existan tests previos que fallen
primero (ciclo Red-Green-Refactor). Toda funcionalidad nueva o cambio de comportamiento
DEBE tener tests escritos y validados antes de iniciar la implementación correspondiente.

**Rationale**: Previene regresiones y obliga a definir el comportamiento esperado antes
de escribir código, lo cual es especialmente crítico al manipular datos personales.

### III. Privacidad por Diseño

Los datos personales NUNCA deben registrarse (loguearse) en texto plano, en ningún
nivel de log (debug, info, warning, error). Todo campo sensible (p. ej. CURP, RFC,
teléfono, correo electrónico, dirección) DEBE validarse y sanitizarse en la capa de
entrada antes de procesarse o persistirse. Los logs y los mensajes de error DEBEN
enmascarar u omitir dichos valores.

**Rationale**: El proyecto maneja datos personales; la fuga vía logs es una de las
causas más comunes de incidentes de privacidad y puede tener implicaciones legales.

### IV. Simplicidad

El proyecto se mantiene como una sola aplicación desplegable. No se introducen
microservicios, colas de mensajes ni capas de abstracción adicionales (patrones,
frameworks internos, wrappers) salvo que exista una necesidad concreta y demostrada.
El principio YAGNI aplica por defecto.

**Rationale**: Mantener el sistema simple reduce la superficie de fallo y facilita la
auditoría de seguridad y privacidad requerida por el Principio III.

### V. Consistencia en Manejo de Errores

Toda respuesta de error de la API DEBE usar un formato JSON consistente que incluya,
como mínimo, un código de error y un mensaje. No se permiten formatos de error ad-hoc
por endpoint. El formato exacto DEBE documentarse en la especificación OpenAPI y
aplicarse mediante un manejador de errores centralizado.

**Rationale**: Un contrato de error uniforme simplifica el manejo en los clientes y
evita fugas accidentales de información sensible en mensajes de error no estandarizados.

### VI. Catálogos de Referencia Locales e Idempotentes

Los catálogos de referencia externos (p. ej. códigos postales SEPOMEX) se almacenan en
la base de datos local de la aplicación. Su actualización se realiza mediante un proceso
de carga (ETL/seed) que DEBE ser idempotente: ejecutarlo múltiples veces con los mismos
datos de origen DEBE producir el mismo estado final, sin duplicados ni efectos colaterales.

**Rationale**: Evita dependencias en tiempo real de servicios externos, mejora
disponibilidad y latencia, y garantiza que las actualizaciones de catálogos sean seguras
de repetir tras errores o nuevas versiones de datos.

## Restricciones Adicionales

Dado que el proyecto maneja datos personales, además de los principios anteriores:

- Toda comunicación externa de la API DEBE usar HTTPS/TLS.
- Los campos sensibles DEBEN cifrarse en reposo cuando la naturaleza del dato lo
  requiera (p. ej. identificadores oficiales).
- El acceso a datos personales DEBE estar sujeto a autenticación y autorización
  explícitas; no existen endpoints de lectura de datos personales sin control de acceso.

## Flujo de Desarrollo

- Todo cambio de API (endpoint nuevo o cambio de contrato) requiere actualizar la
  especificación OpenAPI en el mismo cambio/PR (Principio I).
- Todo cambio de código requiere tests escritos y en estado fallido antes de la
  implementación (Principio II), y revisión de que no se introduzcan logs con datos
  personales en texto plano (Principio III).
- Los procesos de carga de catálogos (Principio VI) DEBEN incluir una prueba que
  verifique la idempotencia (ejecutar el proceso dos veces y comparar el estado
  resultante).
- Todo PR que agregue o modifique un endpoint DEBE verificar que las respuestas de
  error sigan el formato consistente definido en el Principio V.

## Governance

Esta constitución prevalece sobre cualquier otra práctica, guía o convención del
proyecto. En caso de conflicto entre esta constitución y otra documentación (README,
comentarios de código, decisiones ad-hoc), la constitución tiene prioridad.

**Enmiendas**: Cualquier cambio a esta constitución (adición, modificación o eliminación
de un principio) requiere: (1) documentar la propuesta y su justificación, (2) actualizar
este archivo con el nuevo número de versión conforme al versionado semántico descrito
abajo, y (3) revisar los templates dependientes (`plan-template.md`, `spec-template.md`,
`tasks-template.md`) para mantener la consistencia.

**Versionado**: MAJOR para eliminaciones o redefiniciones incompatibles de principios;
MINOR para la adición de nuevos principios o expansión material de una guía existente;
PATCH para aclaraciones o correcciones de redacción sin cambio semántico.

**Cumplimiento**: Todo PR o revisión de código DEBE verificar el cumplimiento de los
principios anteriores. Cualquier desviación (p. ej. una excepción al principio de
Simplicidad para un caso concreto) DEBE justificarse explícitamente en la descripción
del PR, incluyendo la alternativa más simple considerada y por qué fue rechazada.

**Versión**: 1.0.0 | **Ratificada**: 2026-07-02 | **Última Enmienda**: 2026-07-02

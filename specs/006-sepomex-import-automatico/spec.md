# Feature Specification: Automatización de la Actualización del Catálogo SEPOMEX

**Feature Branch**: `006-sepomex-import-automatico`

**Created**: 2026-07-06

**Status**: Draft

**Input**: User description: "Automatizar la actualización del catálogo de códigos postales SEPOMEX. Orquesta el importador idempotente ya existente, no lo reescribe. Ejecución programada (job periódico, calendarizable, default semanal) sobre un directorio configurado: archivo nuevo se importa y se archiva como procesado. Disparo manual: endpoint solo ADMIN para subir un archivo y ejecutar la importación inmediatamente, con resumen (insertados/actualizados/sin cambio/rechazados). Bitácora de corridas (fecha, origen, usuario si aplica, archivo, duración, resumen, estado) consultable solo por ADMIN, paginada. Nunca dos importaciones a la vez (rechazar o encolar la segunda, decidir en clarify) y queda en bitácora. Archivo inválido no deja el catálogo a medias (verificación previa; si falla, no se toca el catálogo). La importación no degrada la API (consultas de CP responden durante la carga; el caché se invalida al terminar una importación exitosa)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Actualización automática y periódica del catálogo (Priority: P1)

Como operador responsable del catálogo de códigos postales, necesito que el sistema
revise por sí solo, de forma periódica, si hay una versión nueva del archivo oficial de
SEPOMEX en un directorio designado, y que la importe automáticamente sin que nadie
tenga que ejecutar nada manualmente cada vez que SEPOMEX publica una actualización.

**Why this priority**: Es el objetivo central del feature — automatizar lo que hoy
requiere un paso manual (arrancar la aplicación con un argumento). Sin esto, el resto
del feature (disparo manual, bitácora) solo sería una mejora incremental sobre el
proceso manual ya existente.

**Independent Test**: Colocando un archivo de catálogo válido y no procesado antes en
el directorio configurado, y dejando pasar un ciclo del job, se puede verificar
completamente que el catálogo se actualiza, el archivo queda archivado como procesado,
y aparece una corrida exitosa en la bitácora. No depende de US2 ni de US3 para
demostrar su valor.

**Acceptance Scenarios**:

1. **Given** un archivo de catálogo válido y nunca antes procesado en el directorio
   configurado, **When** transcurre un ciclo del job programado, **Then** el catálogo
   se actualiza, el archivo queda archivado como procesado, y la corrida aparece en la
   bitácora con estado exitoso.
2. **Given** un archivo ya procesado y archivado anteriormente, **When** transcurren
   ciclos posteriores del job, **Then** ese archivo no se vuelve a importar.
3. **Given** un archivo con estructura inválida (corrupto) en el directorio, **When**
   el job lo detecta, **Then** el catálogo permanece exactamente igual que antes, y la
   corrida queda registrada en la bitácora con estado de error y detalle del problema.

---

### User Story 2 - Disparo manual con resultado inmediato (Priority: P1)

Como ADMIN, necesito poder subir un archivo de catálogo y ejecutar la importación de
inmediato, sin esperar al siguiente ciclo programado, y ver de inmediato cuántos
registros se insertaron, actualizaron, quedaron sin cambio o fueron rechazados.

**Why this priority**: Cubre el caso operativo de no poder esperar al ciclo periódico
(p. ej. una corrección urgente publicada por SEPOMEX). Es independiente de US1: puede
probarse y aportar valor aunque el job programado nunca se haya ejecutado todavía.

**Independent Test**: Autenticando como ADMIN y subiendo un archivo válido al endpoint
manual, se puede verificar completamente que la respuesta incluye el resumen de
insertados/actualizados/sin cambio/rechazados, y que subir el mismo archivo una segunda
vez responde con 0 insertados (idempotencia visible), sin depender del job programado.

**Acceptance Scenarios**:

1. **Given** un ADMIN autenticado con un archivo de catálogo válido, **When** lo sube
   al endpoint de disparo manual, **Then** la importación se ejecuta de inmediato y la
   respuesta incluye el resumen (insertados, actualizados, sin cambio, rechazados).
2. **Given** ese mismo archivo ya importado, **When** un ADMIN lo vuelve a subir sin
   ningún cambio, **Then** la respuesta reporta 0 insertados (y 0 actualizados, si
   tampoco cambió ningún dato).
3. **Given** un usuario con rol CAPTURISTA autenticado, **When** intenta usar el
   endpoint de disparo manual, **Then** el sistema responde 403.
4. **Given** un archivo corrupto subido manualmente, **When** se intenta importar,
   **Then** el sistema responde con un error claro, el catálogo no cambia, y la corrida
   queda en la bitácora con estado de error.

---

### User Story 3 - Consultar la bitácora de corridas (Priority: P2)

Como ADMIN, necesito poder ver el historial de todas las corridas de importación
(programadas y manuales), con su resultado, para poder confirmar que las
actualizaciones periódicas están ocurriendo correctamente o diagnosticar por qué una
falló.

**Why this priority**: Es una capacidad de supervisión que depende de que existan
corridas que consultar (US1/US2 ya las producen); no bloquea la automatización en sí,
pero es necesaria para poder confiar en ella sin revisar logs de servidor a mano.

**Independent Test**: Con al menos una corrida ya registrada (de cualquier origen), se
puede verificar completamente autenticando como ADMIN, consultando la bitácora
paginada, y confirmando que la corrida aparece con todos sus datos (fecha, origen,
usuario si aplica, archivo, duración, resumen, estado).

**Acceptance Scenarios**:

1. **Given** al menos una corrida programada y una manual ya registradas, **When** un
   ADMIN consulta la bitácora, **Then** ambas aparecen, cada una indicando su origen,
   fecha, duración, resumen y estado (la manual además indica qué usuario la disparó).
2. **Given** un usuario con rol CAPTURISTA autenticado, **When** intenta consultar la
   bitácora, **Then** el sistema responde 403.

---

### Edge Cases

- Dos disparos de importación casi simultáneos (programado y manual, o dos manuales)
  nunca ejecutan al mismo tiempo; el segundo se rechaza de inmediato (no se encola) y
  queda registrado en la bitácora reflejando ese rechazo (FR-010).
- Un archivo cuya estructura es válida en general pero contiene filas individuales con
  datos inválidos: esas filas se cuentan como "rechazadas" en el resumen, sin abortar
  la importación completa del resto de filas válidas (a diferencia de un archivo cuya
  estructura general es inválida, que se rechaza por completo antes de tocar el
  catálogo).
- Si el paso de archivar un archivo ya importado exitosamente llegara a fallar, el
  sistema NO debe volver a importarlo en el siguiente ciclo solo porque siga presente
  en el directorio: la bitácora (no solo la ubicación física del archivo) es la fuente
  de verdad de qué ya se procesó.
- Una consulta de código postal ejecutada mientras una importación está en curso debe
  responder con normalidad (datos previos a la importación en curso, hasta que esta
  termine e invalide el caché).
- Una importación que falla no invalida el caché de códigos postales (los datos no
  cambiaron).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE revisar periódicamente un directorio configurado en
  busca de archivos de catálogo nuevos, con una periodicidad configurable (valor por
  defecto: semanal).
- **FR-002**: Cuando el job programado encuentra un archivo no procesado antes, el
  sistema DEBE importarlo usando el mecanismo de importación idempotente ya existente,
  y luego archivarlo como procesado.
- **FR-003**: El sistema DEBE determinar si un archivo ya fue procesado consultando el
  registro de corridas (bitácora), no únicamente su ubicación física, de modo que un
  archivo con una corrida exitosa ya registrada nunca se reimporte aunque el paso de
  archivado físico hubiera fallado.
- **FR-004**: El sistema DEBE exponer un endpoint para que un ADMIN suba un archivo de
  catálogo y dispare su importación de inmediato, sin esperar al ciclo programado.
- **FR-005**: El disparo manual DEBE estar restringido a usuarios con rol ADMIN,
  respondiendo 403 a cualquier otro rol.
- **FR-006**: La respuesta del disparo manual DEBE incluir un resumen con la cantidad
  de filas insertadas, actualizadas, sin cambio, y rechazadas.
- **FR-007**: El sistema DEBE registrar en una bitácora cada corrida de importación
  (programada o manual), incluyendo: fecha/hora, origen (programada o manual), usuario
  (cuando el origen es manual), archivo procesado, duración, resumen de resultados, y
  estado (éxito, o error con detalle del problema).
- **FR-008**: El sistema DEBE exponer un endpoint para que un ADMIN consulte la
  bitácora de corridas, paginado, respondiendo 403 a cualquier otro rol.
- **FR-009**: El sistema NUNCA DEBE ejecutar dos importaciones al mismo tiempo,
  independientemente de si cada una se originó de forma programada o manual.
- **FR-010**: Cuando una importación se dispara mientras otra ya está en curso, el
  sistema DEBE rechazarla de inmediato (no encolarla): el disparo manual recibe un
  error indicando que ya hay una importación en curso, y el ciclo programado que
  coincide con una importación en curso simplemente se salta, esperando al siguiente
  ciclo. En cualquier caso, DEBE quedar registrada en la bitácora reflejando lo que le
  ocurrió.
- **FR-011**: Antes de modificar el catálogo, el sistema DEBE verificar la estructura
  general del archivo (formato/columnas esperadas); si esa verificación falla, el
  sistema NO DEBE modificar el catálogo en absoluto, y la corrida DEBE quedar
  registrada en la bitácora con estado de error y detalle del problema.
- **FR-012**: Dentro de un archivo con estructura general válida, una fila individual
  con datos inválidos DEBE contarse como rechazada en el resumen, sin impedir que el
  resto de filas válidas del mismo archivo se importen.
- **FR-013**: Las consultas existentes de códigos postales DEBEN seguir respondiendo
  con normalidad mientras una importación está en curso.
- **FR-014**: El sistema DEBE invalidar el caché de consultas de códigos postales
  únicamente cuando una importación termina exitosamente (con al menos un cambio
  aplicado); una importación fallida o rechazada por completo no invalida el caché.
- **FR-015**: Subir el mismo archivo ya importado exitosamente una segunda vez (vía el
  disparo manual) DEBE reportar 0 filas insertadas para los datos que no cambiaron
  (idempotencia visible en el resumen).
- **FR-016**: La suite de tests automatizados existente DEBE seguir pasando en su
  totalidad tras este cambio.

### Key Entities *(include if feature involves data)*

- **Corrida de Importación (bitácora)**: registro de una ejecución del proceso de
  importación. Atributos: fecha/hora de inicio, origen (programada/manual), usuario
  (si el origen es manual), archivo procesado (identificador/nombre), duración,
  resumen (insertados, actualizados, sin cambio, rechazados), estado (éxito/error) y
  detalle cuando hay error.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los archivos válidos y no procesados colocados en el
  directorio configurado quedan importados, archivados y registrados en la bitácora
  dentro de un ciclo del job programado.
- **SC-002**: Subir el mismo archivo dos veces por el disparo manual reporta 0 filas
  insertadas en la segunda ejecución para los datos ya existentes.
- **SC-003**: El 100% de los archivos con estructura inválida deja el catálogo
  exactamente sin cambios y queda registrado en la bitácora con estado de error.
- **SC-004**: El 100% de los intentos de disparo simultáneo resulta en exactamente una
  importación ejecutándose a la vez, con el resto reflejado en la bitácora.
- **SC-005**: El 100% de las consultas de códigos postales realizadas mientras una
  importación está en curso responde con normalidad, sin errores ni tiempos de espera
  perceptiblemente mayores.
- **SC-006**: El 100% de los intentos de un rol distinto de ADMIN de usar el disparo
  manual o de consultar la bitácora son rechazados.
- **SC-007**: La suite de tests automatizados existente mantiene una tasa de éxito del
  100% después del cambio.

## Assumptions

- **Corrección de contexto sobre el importador existente**: el contexto de esta
  especificación menciona que el archivo de SEPOMEX se lee en `ISO-8859-1`; al revisar
  `SepomexImportService` (código actual), la lectura es en `UTF-8`, y el archivo de
  catálogo ya presente en el repositorio también es `UTF-8`. Como este feature orquesta
  el importador existente sin reescribirlo, no se asume ningún cambio de codificación;
  si el archivo real que publica SEPOMEX en producción está en `ISO-8859-1`, ese es un
  ajuste al importador existente, fuera del alcance de este feature.
- El importador existente hoy devuelve solo un conteo total de filas procesadas, sin
  distinguir insertadas/actualizadas/sin cambio/rechazadas, y aborta la importación
  completa ante la primera fila con formato inválido (todo-o-nada). Para que el
  disparo manual (FR-006) y el comportamiento de filas rechazadas (FR-012) sean
  posibles, se asume que el importador existente necesita devolver un resumen más
  detallado y tolerar filas individualmente inválidas sin abortar el resto — un ajuste
  acotado a su forma de reportar resultados, no una reescritura de su lógica de
  importación/upsert. Se deja como decisión de diseño para la fase de planeación.
- Este feature depende de `002-autenticacion-autorizacion` (rol ADMIN) para restringir
  el disparo manual y la consulta de bitácora.
- El bloqueo de "nunca dos importaciones a la vez" se asume acotado a una sola
  instancia de la aplicación (consistente con el resto del sistema, desplegado como
  una sola aplicación — Principio de Simplicidad de la constitución); no se asume
  coordinación entre múltiples instancias desplegadas simultáneamente.
- El directorio observado por el job programado y el lugar donde se archivan los
  archivos procesados son configurables; los detalles exactos de esa configuración se
  resuelven en la fase de planeación.

# Research: Búsqueda Avanzada de Personas

## 1. Endpoint: extender `GET /api/personas`, no crear uno nuevo

**Decision**: Todos los criterios nuevos se agregan como `@RequestParam` opcionales
adicionales al endpoint ya existente `GET /api/personas` (mismo path, mismo método,
mismo `PersonaPageResponseDTO`/`PersonaResumenDTO` de respuesta). No se crea un
`GET /api/personas/buscar` separado.

**Rationale**: FR-013 exige explícitamente que una llamada sin ningún parámetro nuevo
responda de forma idéntica a como responde hoy; el propio enunciado del feature dice
"extiende el listado existente... sin romperlo". Un endpoint nuevo duplicaría lógica de
paginación/autorización y complicaría mantener ambos sincronizados (Principio I).

**Alternativas consideradas**: Endpoint separado `/api/personas/buscar` — rechazado
porque introduce dos rutas con semántica solapada y el riesgo de que diverjan; además
`GET /api/personas` ya está pensado como "el listado", no hay una necesidad real de
distinguir "listar" de "buscar".

## 2. Nombre de parámetro para estado activo/eliminado: `estadoRegistro`, no `estado`

**Decision**: El nuevo criterio de estado activo/eliminado se expone como
`estadoRegistro` (valores: `ACTIVAS` [por defecto], `ELIMINADAS`, `TODAS`), **no**
como `estado`.

**Rationale**: `estado` ya es, hoy, el nombre del parámetro geográfico (estado de la
dirección vigente, FR-005). Reusar el mismo nombre para dos conceptos distintos
(geográfico vs. activo/eliminado) sería ambiguo para cualquier consumidor de la API y
constituiría, en la práctica, un cambio de significado de un parámetro existente —
exactamente lo que el Principio II prohíbe sin aprobación explícita. `estadoRegistro`
es inequívoco y aditivo.

**Alternativas consideradas**: Un booleano `incluirEliminadas` — rechazado porque no
puede expresar el tercer valor "ambas a la vez" (`TODAS`) que las Assumptions del spec
piden explícitamente para ADMIN.

## 3. Efecto solo-ADMIN de `estadoRegistro`: resuelto en el controller, servicio agnóstico de roles

**Decision**: `PersonaController` determina si el usuario autenticado tiene
`ROLE_ADMIN` (vía `SecurityContextHolder`, mismo mecanismo que ya usa
`JwtAuthenticationFilter` para asignar autoridades) y resuelve el valor *final* y ya
autorizado de `estadoRegistro` **antes** de invocar a `PersonaService`. Si no es ADMIN,
se fuerza a `ACTIVAS` sin importar lo que se haya enviado — sin lanzar error (FR-008).
`PersonaService` recibe siempre el valor ya resuelto y no conoce roles.

**Rationale**: Mantiene la separación actual (Principio I): hoy toda la lógica de rol
vive en `@PreAuthorize` a nivel de controller; `PersonaService` nunca ha necesitado
saber qué rol hace la llamada. Resolverlo en el controller evita introducir esa
dependencia nueva en el servicio y hace trivial testear ambos casos (ADMIN/CAPTURISTA)
a nivel de `@WebMvcTest` sin mockear roles dentro del servicio.

## 4. Búsqueda de texto insensible a acentos: extensión `unaccent` + función IMMUTABLE

**Decision**: Migración nueva que (a) crea la extensión `unaccent` si no existe, y (b)
define un wrapper `IMMUTABLE` sobre ella:

```sql
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE OR REPLACE FUNCTION unaccent_immutable(text)
RETURNS text AS $$
  SELECT unaccent('unaccent', $1)
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;

CREATE INDEX idx_persona_nombre_completo_unaccent
    ON persona (LOWER(unaccent_immutable(nombres || ' ' || apellidos)));
```

La comparación en la Specification usa `LOWER(unaccent_immutable(nombres || ' ' ||
apellidos)) LIKE LOWER(unaccent_immutable(:texto))` (con `%...%` alrededor de `:texto`),
vía `CriteriaBuilder.function("unaccent_immutable", String.class, ...)`.

**Rationale**: `unaccent()` de PostgreSQL está marcada `STABLE`, no `IMMUTABLE` (depende
del diccionario de búsqueda de texto activo), por lo que **no puede usarse directamente
en un índice de expresión** — Postgres rechaza la creación del índice
("functions in index expression must be marked IMMUTABLE"). El patrón estándar y
documentado para este caso es envolverla en una función SQL propia que fija
explícitamente el diccionario (`'unaccent'`) y se declara `IMMUTABLE`. Sin este wrapper,
la migración fallaría en producción, no en desarrollo (donde a veces no hay datos
suficientes para notar la falta de índice), así que se resuelve aquí y no se deja para
descubrir en implementación.

**Alternativas consideradas**: `pg_trgm` + índice GIN — explícitamente pospuesto por
indicación directa: empezar con el índice funcional simple (btree sobre la expresión) y
medir; evaluar `pg_trgm` solo si el volumen de datos lo justifica. `municipio`/`estado`
geográficos **no** llevan `unaccent` — FR-005 exige conservar su comportamiento actual
(`LOWER(...) LIKE LOWER(...)` sin acentos-insensibles), no se altera.

## 5. Rango de edad: fechas límite calculadas en el servicio, no funciones por fila

**Decision**: `edadMinima`/`edadMaxima` (años) se convierten, en `PersonaService`, a un
rango sobre `fecha_nacimiento` **antes** de construir la Specification — nunca se
calcula `EXTRACT(YEAR FROM AGE(...))` ni ninguna función por fila en SQL:

- `edadMinima` → `fechaNacimiento <= hoy.minusYears(edadMinima)`
- `edadMaxima` → `fechaNacimiento >= hoy.minusYears(edadMaxima + 1).plusDays(1)`

Verificado con casos borde (persona que cumple años exactamente hoy):
para `edadMaxima = 65` y hoy = 2026-07-06, el límite inferior es `1960-07-07`; una
persona nacida `1960-07-07` cumple 65 exactamente hoy (incluida, `<= 65`), una nacida un
día antes (`1960-07-06`) ya cumplió 66 (excluida). Para `edadMinima = 18` con el mismo
hoy, el límite superior es `2008-07-06`; nacida ese día cumple 18 exactamente hoy
(incluida, `>= 18`), nacida un día después (`2008-07-07`) aún no cumple 18 (excluida).

**Rationale**: Petición explícita — una función por fila (`AGE(fecha_nacimiento)` o
similar) sobre una columna sin índice de expresión correspondiente fuerza un *sequential
scan*, invalidando cualquier índice normal sobre `fecha_nacimiento`. Calcular las fechas
límite una sola vez en el servicio permite comparar `fecha_nacimiento` con literales,
lo cual sí puede usar un índice B-tree normal. El cálculo de límites es la parte
delicada (off-by-one en el cumpleaños exacto) — ya verificado arriba con casos
concretos; la tarea de implementación debe incluir un test unitario dedicado a estos
dos casos borde exactos, no solo casos "obviamente dentro/fuera de rango".

## 6. Municipio/estado (geográfico): subquery correlacionada, no un nuevo `@OneToMany`

**Decision**: La `Specification` para `municipio`/`estado` reproduce, dentro de una
subquery de Criteria API (`query.subquery(Direccion.class)` correlacionada por
`persona.id`), la misma semántica `EXISTS (...)` que ya usa hoy `buscarActivas` en JPQL.
No se agrega una relación `@OneToMany` inversa en `Persona`.

**Rationale**: `Persona` no tiene hoy un mapeo de colección hacia `Direccion` (la
relación existe solo desde `Direccion.persona`); agregar una `@OneToMany` nueva sería un
cambio de mapeo de entidad más amplio que lo que este feature necesita, y no está
motivado por ningún requisito (FR-005 solo pide conservar el comportamiento actual). Una
subquery correlacionada logra lo mismo sin tocar el mapeo de `Persona`.

## 7. `buscarActivas` (JPQL) se reemplaza por una `Specification` única, no se duplica

**Decision**: El método `PersonaRepository.buscarActivas(...)` (JPQL con 3 parámetros)
se **retira** y se reemplaza por una única `Specification<Persona>` compuesta
dinámicamente (un predicado por criterio presente, incluidos los 3 ya existentes:
nombre/municipio/estado) invocada vía `personaRepository.findAll(Specification,
Pageable)` — método que `JpaSpecificationExecutor<Persona>` ya provee (la interfaz ya
lo extiende hoy, sin usarlo).

**Rationale**: Mantener dos caminos de construcción de query (JPQL fijo de 3 parámetros
+ Specification nueva para el resto) violaría el Principio I (patrones paralelos
inconsistentes) y arriesgaría que ambos diverjan silenciosamente. Consolidar en un solo
enfoque (Specifications) es la aplicación correcta de "no introducir un patrón nuevo
sin necesidad" — aquí sí hay necesidad (los filtros combinables con AND lo piden
estructuralmente), así que se sustituye enteramente, no se agrega en paralelo.
`PersonaServiceTest`/`PersonaControllerListTest` existentes deben seguir pasando sin
cambiar sus aserciones (mismo comportamiento observable, implementación distinta).

## 8. Validación: Bean Validation para campos simples, excepción imperativa para cruces

**Decision**: `PersonaBusquedaFiltroDTO` (record) se popula desde query params vía
`@Valid @ModelAttribute` (soportado por Spring MVC con records desde Spring Framework
6.1 / Boot 3.2, y este proyecto ya está en Boot 3.3.5). Restricciones de un solo campo
(`edadMinima >= 0`, `edadMaxima >= 0`) usan `@Min(0)` de Bean Validation — ya
manejado hoy por `GlobalExceptionHandler.handleValidation` (`MethodArgumentNotValidException`
→ `VALIDACION_FALLIDA` 400 con `campo` preciso). Las validaciones que cruzan dos campos
(`edadMinima > edadMaxima`, `fechaRegistroDesde > fechaRegistroHasta`) y la whitelist de
`ordenarPor`/`direccionOrden` se validan de forma imperativa en `PersonaService`,
lanzando `FormatoInvalidoException(ErrorCode.VALIDACION_FALLIDA, "<parametro>",
"<mensaje>")` — la misma excepción ya usada hoy para `fechaNacimiento` futura.

**Rationale**: Bean Validation a nivel de clase (`@AssertTrue` en un método del record)
sí es posible, pero el `campo` que terminaría en el `ApiError.detalles` sería el nombre
del método/propiedad sintética (p. ej. `rangoEdadValido`), no el parámetro de query real
(`edadMinima` o `edadMaxima`) que pide FR-014 ("indicando el parámetro específico").
La excepción imperativa ya usada en el proyecto da control total sobre qué campo se
reporta, sin necesitar tocar `GlobalExceptionHandler`. `ordenarPor`/`direccionOrden` se
reciben como `String` (no como enum en la firma del método), evitando que un valor
inválido dispare `MethodArgumentTypeMismatchException` — excepción que hoy **no** está
manejada por `GlobalExceptionHandler` y caería en un 500 genérico, violando FR-016.

**Alternativas consideradas**: Manejar `MethodArgumentTypeMismatchException` en
`GlobalExceptionHandler` — rechazado por ahora: cambiaría un componente compartido por
todos los endpoints para resolver un caso que la validación imperativa ya cubre sin
tocarlo, manteniendo el cambio acotado a este feature (Principio I).

## 9. Formato/orden por defecto sin criterio de ordenamiento: sin `ORDER BY` añadido

**Decision**: Cuando no se envía `ordenarPor`, la `Specification`/`Pageable` no agrega
ningún `Sort` — igual que hoy (`PersonaController.listar` construye `PageRequest.of(page,
tamano)` sin `Sort`, orden no garantizado por la base de datos).

**Rationale**: FR-011 exige que, sin criterio de ordenamiento, el comportamiento sea
idéntico al actual; el actual no ordena explícitamente, así que no ordenar por defecto
es, por construcción, idéntico.

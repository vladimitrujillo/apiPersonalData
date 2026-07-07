# Data Model: Búsqueda Avanzada de Personas

No se agregan ni modifican entidades JPA ni columnas de esquema (más allá de la
extensión/función/índice de PostgreSQL descritos en research.md §4). Este feature es
puramente de consulta sobre `Persona`/`Direccion` ya existentes.

## Filtro de búsqueda: `PersonaBusquedaFiltroDTO` (record, nuevo)

Puebla desde `@RequestParam` de `GET /api/personas` vía `@Valid @ModelAttribute`. Todos
los campos son opcionales (`null` = criterio no aplicado).

| Campo | Tipo | Bean Validation | Origen |
|---|---|---|---|
| `nombre` | `String` | — | existente (FR-001, ahora insensible a acentos) |
| `municipio` | `String` | — | existente (FR-005, sin cambio) |
| `estado` | `String` | — | existente (FR-005, sin cambio — geográfico) |
| `curpPrefijo` | `String` | — | nuevo (FR-002) |
| `edadMinima` | `Integer` | `@Min(0)` | nuevo (FR-003) |
| `edadMaxima` | `Integer` | `@Min(0)` | nuevo (FR-003) |
| `fechaRegistroDesde` | `LocalDate` | — | nuevo (FR-004) |
| `fechaRegistroHasta` | `LocalDate` | — | nuevo (FR-004) |
| `sexo` | `String` | — | nuevo (FR-006), coincidencia exacta |
| `estadoRegistro` | `String` | — | nuevo (FR-007/FR-008); valores `ACTIVAS`\|`ELIMINADAS`\|`TODAS`, validado imperativamente |
| `ordenarPor` | `String` | — | nuevo (FR-010); valores `NOMBRE`\|`FECHA_NACIMIENTO`\|`FECHA_REGISTRO`, validado imperativamente |
| `direccionOrden` | `String` | — | nuevo (FR-010); valores `ASC`\|`DESC` (default `ASC` si se omite habiendo `ordenarPor`), validado imperativamente |

Validaciones cruzadas (imperativas, en `PersonaService`, ver research.md §8):

- `edadMinima > edadMaxima` (ambos presentes) → 400, campo `edadMaxima` (FR-014)
- `fechaRegistroDesde > fechaRegistroHasta` (ambos presentes) → 400, campo
  `fechaRegistroHasta` (FR-015)
- `ordenarPor` fuera de la whitelist → 400, campo `ordenarPor` (FR-016)
- `direccionOrden` fuera de la whitelist → 400, campo `direccionOrden` (FR-016)

## Resolución de `estadoRegistro` por rol (no es parte del DTO validado — ver research.md §3)

| Rol autenticado | Valor enviado | Valor efectivo aplicado |
|---|---|---|
| ADMIN | (omitido) | `ACTIVAS` (default, igual que hoy) |
| ADMIN | `ACTIVAS` / `ELIMINADAS` / `TODAS` | el valor enviado, tal cual |
| CAPTURISTA | cualquiera (incluido `ELIMINADAS`/`TODAS`) | `ACTIVAS`, sin error (FR-008) |

## Composición de la Specification (`PersonaSpecifications`, nuevo)

Un método estático por criterio, todos devolviendo `Specification<Persona>`, compuestos
con `Specification.allOf(...)` (AND implícito — FR-009). Predicados solo se agregan si
el criterio correspondiente no es `null`/vacío:

- `conNombreInsensibleAAcentos(texto)` → `unaccent_immutable(...)` (research.md §4)
- `conCurpPrefijo(prefijo)` → `LIKE 'prefijo%'` (sin `unaccent`, sin acentos en CURP)
- `conFechaNacimientoEntre(desde, hasta)` → límites ya calculados por el servicio
  (research.md §5)
- `conFechaRegistroEntre(desde, hasta)` → sobre `Auditable.createdAt`
- `conMunicipio(texto)` / `conEstadoGeografico(texto)` → subquery correlacionada sobre
  `Direccion` (research.md §6), sin cambio de semántica
- `conSexo(valor)` → igualdad exacta
- `conEstadoRegistro(valorEfectivo)` → `activo = true` / `activo = false` / sin predicado
  (`TODAS`)

Reemplaza por completo a `PersonaRepository.buscarActivas(...)` (research.md §7).

## Respuesta: sin cambios de schema

Reutiliza `PersonaPageResponseDTO`/`PersonaResumenDTO`/`DireccionResumenDTO` ya
existentes (FR-012). No se crea ningún DTO de respuesta nuevo.

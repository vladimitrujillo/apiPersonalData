# Quickstart: Búsqueda Avanzada de Personas

Prerrequisitos: aplicación levantada localmente con PostgreSQL (Docker/Testcontainers
disponible), migraciones `V1`-`V5` aplicadas, `002` (auth) funcionando. Usar un token
ADMIN y uno CAPTURISTA (ver quickstart de `002`).

## US1 — Texto insensible a acentos

1. Crear una persona "José García" (`POST /api/personas`).
2. `GET /api/personas?nombre=jose` → aparece en `contenido`.
3. `GET /api/personas?nombre=garcia` → aparece en `contenido`.
4. `GET /api/personas` (sin parámetros nuevos) → respuesta idéntica a la de antes de
   este feature (mismo shape, mismos resultados).

## US2 — Combinación de criterios

1. Crear varias personas con distintas edades, estados (geográficos) y una eliminada
   lógicamente.
2. `GET /api/personas?nombre=...&edadMinima=18&edadMaxima=40&estado=Jalisco` → solo las
   que cumplen los tres criterios a la vez.
3. Repetir agregando `curpPrefijo`, `fechaRegistroDesde`/`Hasta`, `sexo` → sigue siendo
   intersección.
4. `GET /api/personas?ordenarPor=FECHA_NACIMIENTO&direccionOrden=DESC` sobre el
   resultado anterior → orden correcto.

## US3 — CAPTURISTA nunca ve eliminadas

1. Con la persona eliminada del paso anterior y token CAPTURISTA:
   `GET /api/personas?estadoRegistro=ELIMINADAS` → 200, la eliminada NO aparece.
2. Mismo request con token ADMIN → la eliminada SÍ aparece.

## Validaciones (edge cases)

- `GET /api/personas?edadMinima=-1` → 400, `campo=edadMinima`.
- `GET /api/personas?edadMinima=40&edadMaxima=18` → 400, `campo=edadMaxima`.
- `GET /api/personas?fechaRegistroDesde=2026-01-01&fechaRegistroHasta=2025-01-01` → 400,
  `campo=fechaRegistroHasta`.
- `GET /api/personas?ordenarPor=APELLIDO` → 400, `campo=ordenarPor`.
- `GET /api/personas?curpPrefijo=ZZZZ` (sin coincidencias) → 200, página vacía.

## Regresión obligatoria

- Suite completa (`mvn test` + `mvn verify` para `*IT.java`) en 100% verde, incluida
  `PersonaControllerListTest`/`PersonaServiceTest` ya existentes sin cambios en sus
  aserciones (FR-017, SC-006).

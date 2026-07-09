# Quickstart: Automóviles de Personas y Mantenimientos

Valida manualmente los criterios de aceptación de `spec.md` (SC-001 a
SC-009). Requiere la API corriendo (`docker compose up` o `mvn spring-boot:run`)
y un token de ADMIN (`POST /login`).

## 1. Alta de persona, automóvil y dos mantenimientos (SC-001)

1. Crear una persona (`POST /api/personas`).
2. Registrar un automóvil para esa persona: `POST /api/personas/{id}/automoviles`.
3. Asignarle la profesión "Mecánico" a una persona distinta (`POST /api/personas/{mecanicoId}/profesiones` con `profesionId` de "Mecánico" — ver `007-profesiones-personas`).
4. Registrar un mantenimiento con piezas y ese mecánico: `POST /api/automoviles/{automovilId}/mantenimientos`.
5. Registrar un segundo mantenimiento sin piezas ni mecánico, con fecha posterior y kilometraje mayor.
6. `GET /api/automoviles/{automovilId}/mantenimientos` → **Esperado**: ambos, ordenados por fecha descendente, con piezas/costos/kilometraje/nombre del mecánico donde aplica.

## 2. Validaciones de campo (SC-002)

Repetir el registro de un mantenimiento con: fecha futura, costo negativo, kilometraje negativo → **Esperado**: `400 VALIDACION_FALLIDA` en cada caso, indicando el campo.

## 3. Consistencia de kilometraje (SC-003)

Registrar un tercer mantenimiento con kilometraje menor al del mantenimiento más reciente del automóvil → **Esperado**: `409 KILOMETRAJE_INCONSISTENTE`, indicando el kilometraje y la fecha que lo contradicen.

## 4. Validación del mecánico (SC-004, SC-005)

1. Registrar un mantenimiento con un `mecanicoId` aleatorio (inexistente) → **Esperado**: `400 MECANICO_NO_ENCONTRADO`.
2. Registrar uno con el `mecanicoId` de una persona activa sin la profesión "Mecánico" → **Esperado**: `409 MECANICO_SIN_PROFESION_ACTIVA`.
3. Eliminar lógicamente a la persona mecánico del paso 1.1 y volver a registrar un mantenimiento con su id → **Esperado**: `409 MECANICO_ELIMINADO`.
4. Retirar la profesión "Mecánico" a la persona mecánico usada en el mantenimiento del punto 1.4 anterior (`PATCH .../profesiones/{asignacionId}/retirar`) → `GET /api/mantenimientos/{id}` del mantenimiento ya registrado con ella → **Esperado**: sigue mostrando su nombre sin cambios.

## 5. Automóvil/persona eliminados (SC-006)

1. `DELETE /api/automoviles/{id}` → intentar `POST .../mantenimientos` sobre él → **Esperado**: `409 AUTOMOVIL_ELIMINADO`.
2. Sobre otro automóvil activo, `DELETE /api/personas/{id}` de su dueño → intentar registrar un mantenimiento → **Esperado**: `409 PERSONA_ELIMINADA`.

## 6. VIN/placas (SC-007)

1. Registrar un automóvil con un VIN ya usado por cualquier otro (activo o eliminado) → **Esperado**: `409 AUTOMOVIL_VIN_DUPLICADO`.
2. Dar de baja un automóvil y registrar uno nuevo con las mismas placas → **Esperado**: `201 Created`.

## 7. Matriz de permisos (SC-008)

Como CAPTURISTA, intentar `DELETE`/`POST .../restaurar` sobre un automóvil y sobre un mantenimiento → **Esperado**: `403 ACCESO_DENEGADO` en los 4 casos. Confirmar que `POST`/`PATCH`/`GET` sí funcionan para CAPTURISTA.

## 8. Suite automatizada (SC-009)

```sh
mvn -o test -Dmaven.compiler.useIncrementalCompilation=false
mvn -o verify -Dmaven.compiler.useIncrementalCompilation=false
```

**Esperado**: 100% de los tests (incluyendo los de `007-profesiones-personas` y anteriores) en verde.

-- CURP pasa de unica-solo-entre-activas a unica de forma global (D2)
-- Ver specs/004-restaurar-persona-curp/research.md #2

-- 1. Precondicion: fallar explicitamente si existen CURP duplicados entre CUALQUIER
--    combinacion de registros (activos y/o inactivos) - no solo entre activos.
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

-- 2. Retirar el indice parcial (solo-activos)
DROP INDEX ux_persona_curp_activo;

-- 3. Restriccion unica global
ALTER TABLE persona ADD CONSTRAINT uq_persona_curp UNIQUE (curp);

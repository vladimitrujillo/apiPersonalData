-- Busqueda de texto insensible a acentos sobre nombres/apellidos (FR-001)
-- Ver specs/005-busqueda-avanzada-personas/research.md #4

CREATE EXTENSION IF NOT EXISTS unaccent;

-- unaccent() del core es STABLE (depende del diccionario activo), no se puede usar
-- directamente en un indice de expresion (requiere IMMUTABLE). Se fija el diccionario
-- explicitamente ('unaccent') en un wrapper propio marcado IMMUTABLE.
CREATE OR REPLACE FUNCTION unaccent_immutable(text)
RETURNS text AS $$
    SELECT unaccent('unaccent', $1)
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;

CREATE INDEX idx_persona_nombre_completo_unaccent
    ON persona (LOWER(unaccent_immutable(nombres || ' ' || apellidos)));

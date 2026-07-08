-- Catalogo de profesiones y asignacion a personas (FR-001 a FR-025)
-- Ver specs/007-profesiones-personas/data-model.md

-- Precaucion idempotente: la extension y la funcion ya existen desde V5, pero un
-- entorno que aplicara V7 sin haber corrido V5 antes las necesitaria (research.md #1-2).
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE OR REPLACE FUNCTION unaccent_immutable(text)
RETURNS text AS $$
    SELECT unaccent('unaccent', $1)
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;

CREATE TABLE profesion (
    id              BIGSERIAL PRIMARY KEY,
    nombre          VARCHAR(80) NOT NULL,
    descripcion     TEXT,
    activo          BOOLEAN NOT NULL DEFAULT true,
    creado_por      UUID REFERENCES usuario (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_por UUID REFERENCES usuario (id),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Unicidad insensible a mayusculas/acentos, activa o desactivada (FR-004, research.md #1)
CREATE UNIQUE INDEX ux_profesion_nombre_unaccent
    ON profesion (LOWER(unaccent_immutable(nombre)));

-- Semilla (FR-002); INSERT idempotente por si el entorno se recrea desde cero.
INSERT INTO profesion (nombre, descripcion, activo)
SELECT 'Mecánico', NULL, true
WHERE NOT EXISTS (
    SELECT 1 FROM profesion WHERE LOWER(unaccent_immutable(nombre)) = LOWER(unaccent_immutable('Mecánico'))
);

CREATE TABLE persona_profesion (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    persona_id      UUID NOT NULL REFERENCES persona (id),
    profesion_id    BIGINT NOT NULL REFERENCES profesion (id),
    fecha_desde     DATE NOT NULL DEFAULT CURRENT_DATE,
    cedula          VARCHAR(30),
    activo          BOOLEAN NOT NULL DEFAULT true,
    creado_por      UUID REFERENCES usuario (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_por UUID REFERENCES usuario (id),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A lo sumo una asignacion activa por par persona-profesion; permite acumular
-- episodios historicos (retirados) del mismo par sin violar la unicidad (research.md #3-4).
CREATE UNIQUE INDEX ux_persona_profesion_activa
    ON persona_profesion (persona_id, profesion_id) WHERE activo = true;

CREATE INDEX ix_persona_profesion_persona_id ON persona_profesion (persona_id);
CREATE INDEX ix_persona_profesion_profesion_id ON persona_profesion (profesion_id);

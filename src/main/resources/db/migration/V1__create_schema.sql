-- Esquema inicial: persona, direccion, cp_catalogo
-- Ver specs/001-personas-codigos-postales/data-model.md

CREATE TABLE persona (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombres           TEXT NOT NULL,
    apellidos         TEXT NOT NULL,
    fecha_nacimiento  DATE NOT NULL,
    sexo              VARCHAR(20) NOT NULL,
    curp              VARCHAR(18) NOT NULL,
    rfc               VARCHAR(13) NOT NULL,
    correo            TEXT NOT NULL,
    telefono          VARCHAR(10) NOT NULL,
    activo            BOOLEAN NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Unicidad de correo/CURP solo entre personas activas (research.md #2)
CREATE UNIQUE INDEX ux_persona_correo_activo ON persona (correo) WHERE activo = true;
CREATE UNIQUE INDEX ux_persona_curp_activo ON persona (curp) WHERE activo = true;

CREATE TABLE cp_catalogo (
    id                  BIGSERIAL PRIMARY KEY,
    codigo_postal       VARCHAR(5) NOT NULL,
    estado              TEXT NOT NULL,
    municipio           TEXT NOT NULL,
    asentamiento        TEXT NOT NULL,
    tipo_asentamiento   TEXT NOT NULL,
    id_asenta_cpcons    VARCHAR(10) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_cp_catalogo_cp_asenta UNIQUE (codigo_postal, id_asenta_cpcons)
);

-- Consulta exacta por CP (FR-013)
CREATE INDEX ix_cp_catalogo_codigo_postal ON cp_catalogo (codigo_postal);

-- Busqueda de colonias acotada por estado/municipio (FR-016)
CREATE INDEX ix_cp_catalogo_estado_municipio_asenta ON cp_catalogo (estado, municipio, asentamiento);

CREATE TABLE direccion (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    persona_id      UUID NOT NULL REFERENCES persona (id),
    calle           TEXT NOT NULL,
    numero          TEXT NOT NULL,
    colonia         TEXT NOT NULL,
    municipio       TEXT NOT NULL,
    estado          TEXT NOT NULL,
    codigo_postal   VARCHAR(5) NOT NULL,
    pais            TEXT NOT NULL,
    cp_catalogo_id  BIGINT NULL REFERENCES cp_catalogo (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_direccion_persona_id ON direccion (persona_id);

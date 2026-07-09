-- Automoviles de personas, mantenimientos y piezas cambiadas (FR-001 a FR-029)
-- Ver specs/008-automoviles-mantenimientos/data-model.md

CREATE TABLE automovil (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    persona_id      UUID NOT NULL REFERENCES persona (id),
    marca           VARCHAR(60) NOT NULL,
    modelo          VARCHAR(60) NOT NULL,
    anio            SMALLINT NOT NULL CHECK (anio >= 1900),
    color           VARCHAR(40),
    placas          VARCHAR(10) NOT NULL,
    vin             VARCHAR(17),
    activo          BOOLEAN NOT NULL DEFAULT true,
    creado_por      UUID REFERENCES usuario (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_por UUID REFERENCES usuario (id),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Placas unicas solo entre activos, reasignables tras baja (analogo a correo, FR-003/FR-004)
CREATE UNIQUE INDEX ux_automovil_placas_activo
    ON automovil (placas) WHERE activo = true;

-- VIN identidad global del vehiculo, sin importar el estado (analogo a CURP, FR-005)
CREATE UNIQUE INDEX ux_automovil_vin ON automovil (vin);

CREATE INDEX ix_automovil_persona_id ON automovil (persona_id);

CREATE TABLE mantenimiento (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    automovil_id    UUID NOT NULL REFERENCES automovil (id),
    descripcion     TEXT NOT NULL,
    fecha           DATE NOT NULL CHECK (fecha <= CURRENT_DATE),
    kilometraje     INT NOT NULL CHECK (kilometraje >= 0),
    mecanico_id     UUID REFERENCES persona (id),
    costo_total     NUMERIC(12,2) NOT NULL CHECK (costo_total >= 0),
    activo          BOOLEAN NOT NULL DEFAULT true,
    creado_por      UUID REFERENCES usuario (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_por UUID REFERENCES usuario (id),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_mantenimiento_automovil_id_fecha ON mantenimiento (automovil_id, fecha DESC);
CREATE INDEX ix_mantenimiento_mecanico_id ON mantenimiento (mecanico_id);

CREATE TABLE pieza_cambiada (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mantenimiento_id UUID NOT NULL REFERENCES mantenimiento (id) ON DELETE CASCADE,
    nombre           VARCHAR(120) NOT NULL,
    numero_parte     VARCHAR(40),
    costo            NUMERIC(12,2) CHECK (costo IS NULL OR costo >= 0)
);

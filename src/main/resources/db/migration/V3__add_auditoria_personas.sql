-- Auditoria "quien/cuando" en persona y direccion + historial inmutable de cambios
-- Ver specs/003-auditoria-personas/data-model.md

ALTER TABLE persona ADD COLUMN creado_por UUID NULL REFERENCES usuario (id);
ALTER TABLE persona ADD COLUMN actualizado_por UUID NULL REFERENCES usuario (id);

ALTER TABLE direccion ADD COLUMN creado_por UUID NULL REFERENCES usuario (id);
ALTER TABLE direccion ADD COLUMN actualizado_por UUID NULL REFERENCES usuario (id);

CREATE TABLE persona_historial (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    persona_id  UUID NOT NULL REFERENCES persona (id),
    usuario_id  UUID NOT NULL REFERENCES usuario (id),
    operacion   VARCHAR(20) NOT NULL
                CHECK (operacion IN ('CREACION', 'MODIFICACION', 'ELIMINACION', 'RESTAURACION')),
    fecha       TIMESTAMPTZ NOT NULL DEFAULT now(),
    cambios     JSONB NOT NULL
);

CREATE INDEX ix_persona_historial_persona_fecha ON persona_historial (persona_id, fecha DESC);

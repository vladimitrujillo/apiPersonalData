-- Bitacora de corridas de importacion del catalogo SEPOMEX (FR-007, data-model.md)
-- Ver specs/006-sepomex-import-automatico/research.md

CREATE TABLE catalogo_importacion (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    origen          VARCHAR(20) NOT NULL CHECK (origen IN ('PROGRAMADA', 'MANUAL')),
    usuario_id      UUID REFERENCES usuario (id),
    archivo         TEXT NOT NULL,
    archivo_hash    TEXT NOT NULL,
    fecha_inicio    TIMESTAMPTZ NOT NULL DEFAULT now(),
    duracion_ms     BIGINT,
    insertados      INT NOT NULL DEFAULT 0,
    actualizados    INT NOT NULL DEFAULT 0,
    sin_cambio      INT NOT NULL DEFAULT 0,
    rechazados      INT NOT NULL DEFAULT 0,
    estado          VARCHAR(30) NOT NULL CHECK (estado IN ('EXITO', 'ERROR', 'RECHAZADA_CONCURRENCIA')),
    detalle_error   TEXT
);

-- Bitacora ordenada del mas reciente al mas antiguo (US3, GET .../importaciones)
CREATE INDEX ix_catalogo_importacion_fecha ON catalogo_importacion (fecha_inicio DESC);

-- "Ya procesado" se determina por hash de contenido, no por nombre (research.md #5)
CREATE INDEX ix_catalogo_importacion_hash_estado ON catalogo_importacion (archivo_hash, estado);

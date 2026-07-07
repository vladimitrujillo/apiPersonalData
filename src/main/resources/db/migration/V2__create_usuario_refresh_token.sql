-- Usuarios del sistema (operadores) y tokens de refresco
-- Ver specs/002-autenticacion-autorizacion/data-model.md

CREATE TABLE usuario (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    login           TEXT NOT NULL,
    password_hash   TEXT NOT NULL,
    nombre          TEXT NOT NULL,
    rol             VARCHAR(20) NOT NULL CHECK (rol IN ('ADMIN', 'CAPTURISTA')),
    activo          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Unicidad global y permanente del login (nunca reutilizable, ni desactivado - FR-011/FR-012)
CREATE UNIQUE INDEX ux_usuario_login ON usuario (login);

CREATE TABLE refresh_token (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      UUID NOT NULL REFERENCES usuario (id),
    token_hash      TEXT NOT NULL,
    expira_en       TIMESTAMPTZ NOT NULL,
    revocado        BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_refresh_token_token_hash ON refresh_token (token_hash);
CREATE INDEX ix_refresh_token_usuario_id ON refresh_token (usuario_id);

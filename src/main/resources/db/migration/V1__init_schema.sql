-- ============================================================
-- Spectrum AI - Schema inicial
-- Multi-tenant: companies como tenant; demais tabelas usam tenant_id
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Empresas (tenants)
CREATE TABLE companies (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(255) NOT NULL,
    tax_id      VARCHAR(64) UNIQUE,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Usuários
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    full_name       VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(32) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_users_tenant ON users(tenant_id);

-- Sessões de análise
CREATE TABLE analysis_sessions (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id   UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    owner_id    UUID NOT NULL REFERENCES users(id),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_sessions_tenant ON analysis_sessions(tenant_id);

-- Pesquisas (cada veículo pesquisado)
CREATE TABLE searches (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id),
    session_id      UUID REFERENCES analysis_sessions(id) ON DELETE SET NULL,
    brand           VARCHAR(100) NOT NULL,
    model           VARCHAR(100) NOT NULL,
    trim            VARCHAR(100),
    model_year      SMALLINT,
    categories      TEXT[] NOT NULL,
    specs           JSONB,
    status          VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    confidence      NUMERIC(3,2),
    failure_reason  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ
);
CREATE INDEX idx_searches_tenant  ON searches(tenant_id);
CREATE INDEX idx_searches_status  ON searches(status);
CREATE INDEX idx_searches_vehicle ON searches(tenant_id, brand, model, trim, model_year);

-- Templates de prompts versionados
CREATE TABLE prompt_templates (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(128) NOT NULL,
    version     INT NOT NULL,
    body        TEXT NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT FALSE,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(name, version)
);
CREATE INDEX idx_prompts_active ON prompt_templates(name) WHERE active = TRUE;

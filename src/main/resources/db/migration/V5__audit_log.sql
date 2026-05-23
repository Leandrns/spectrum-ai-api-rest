-- ============================================================
-- Trilha de auditoria para acoes criticas (LGPD / SOX-like)
-- ============================================================

CREATE TABLE audit_log (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id     UUID,
    actor_id      UUID,
    actor_email   VARCHAR(255),
    action        VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64),
    resource_id   VARCHAR(128),
    ip_address    VARCHAR(64),
    user_agent    VARCHAR(512),
    details       JSONB,
    outcome       VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_tenant_created  ON audit_log(tenant_id, created_at DESC);
CREATE INDEX idx_audit_action          ON audit_log(action, created_at DESC);
CREATE INDEX idx_audit_actor           ON audit_log(actor_id, created_at DESC);

-- Soft-delete: campo deleted_at para purga assincrona
ALTER TABLE searches           ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE analysis_sessions  ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_searches_deleted_at ON searches(deleted_at)
    WHERE deleted_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_sessions_deleted_at ON analysis_sessions(deleted_at)
    WHERE deleted_at IS NOT NULL;

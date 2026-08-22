-- ============================================================
-- Spectrum AI - Exportacoes de dados (CSV / PDF)
--
-- Registro de controle dos arquivos gerados e enviados ao bucket
-- do GCS. Uma linha por (tenant, escopo, recurso, formato): o
-- content_hash permite reaproveitar o objeto ja enviado quando os
-- dados de origem nao mudaram, sem precisar de TTL de cache.
-- ============================================================

CREATE TABLE data_exports (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    -- SEARCH = uma pesquisa; SESSION = todas as pesquisas concluidas da sessao
    scope        VARCHAR(16) NOT NULL,
    -- search_id ou session_id, conforme o scope
    resource_id  UUID NOT NULL,
    format       VARCHAR(8)  NOT NULL,
    object_name  TEXT NOT NULL,
    -- SHA-256 do conteudo gerado; usado para invalidar o cache
    content_hash VARCHAR(64) NOT NULL,
    size_bytes   BIGINT NOT NULL,
    row_count    INT NOT NULL,
    created_by   UUID NOT NULL REFERENCES users(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, scope, resource_id, format)
);

CREATE INDEX idx_data_exports_tenant ON data_exports(tenant_id);

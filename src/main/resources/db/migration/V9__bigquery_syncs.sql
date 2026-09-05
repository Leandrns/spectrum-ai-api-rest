-- ============================================================
-- Spectrum AI - Controle da ingestao no BigQuery
--
-- Uma linha por (tenant, pesquisa). O content_hash e o que evita
-- reenviar linhas identicas: a tabela do BigQuery e append-only
-- (a streaming API nao aceita DML sobre linhas recem-inseridas),
-- entao sem essa guarda cada chamada empilharia uma copia.
--
-- O expurgo do DataRetentionScheduler apaga a pesquisa e, por
-- cascata, este registro. As linhas ja gravadas no BigQuery nao
-- sao afetadas: lah quem descarta e a expiracao de particao.
-- ============================================================

CREATE TABLE bigquery_syncs (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    search_id    UUID NOT NULL REFERENCES searches(id) ON DELETE CASCADE,
    -- SHA-256 do conteudo enviado, sem o carimbo de ingestao
    content_hash VARCHAR(64) NOT NULL,
    row_count    INT NOT NULL,
    -- Carimbo da ultima carga efetiva; corresponde a coluna ingerido_em no BigQuery
    ingested_at  TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, search_id)
);

CREATE INDEX idx_bigquery_syncs_tenant ON bigquery_syncs(tenant_id);

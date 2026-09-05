package com.spectrumai.backend.export.bigquery.repository;

import com.spectrumai.backend.export.bigquery.model.BigQuerySync;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface BigQuerySyncRepository extends JpaRepository<BigQuerySync, UUID> {

    /** Busca pela chave única da tabela — o registro da última carga da pesquisa. */
    Optional<BigQuerySync> findByTenantIdAndSearchId(UUID tenantId, UUID searchId);

    /**
     * Grava o registro de controle resolvendo o conflito na própria escrita.
     *
     * <p>Duas cargas da mesma pesquisa podem correr em paralelo — o gancho automático
     * do fim da pesquisa e uma chamada ao endpoint manual, por exemplo. Ambas leem
     * "não existe registro" e ambas tentam inserir; sem isso, a segunda quebraria em
     * {@code DataIntegrityViolationException} pela unicidade
     * {@code (tenant_id, search_id)} <em>depois</em> de já ter gravado as linhas no
     * BigQuery, fazendo a requisição falhar sem que houvesse nada de errado.
     *
     * <p>É {@code INSERT ... ON CONFLICT DO UPDATE} em vez de capturar a violação e
     * reler porque uma falha de flush deixa o {@code EntityManager} num estado do qual
     * não se recupera: a re-tentativa dentro da mesma transação falharia de novo. Aqui
     * o banco resolve o conflito numa única instrução, sem exceção nenhuma.
     *
     * <p>Vence a última carga a gravar, que é o que se quer: as linhas do BigQuery
     * dessa corrida têm conteúdo idêntico, e a view de leitura já escolhe uma só.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO bigquery_syncs
                (id, tenant_id, search_id, content_hash, row_count, ingested_at, created_at, updated_at)
            VALUES
                (:id, :tenantId, :searchId, :contentHash, :rowCount, :ingestedAt, NOW(), NOW())
            ON CONFLICT (tenant_id, search_id) DO UPDATE SET
                content_hash = EXCLUDED.content_hash,
                row_count    = EXCLUDED.row_count,
                ingested_at  = EXCLUDED.ingested_at,
                updated_at   = NOW()
            """, nativeQuery = true)
    void upsert(@Param("id") UUID id,
                @Param("tenantId") UUID tenantId,
                @Param("searchId") UUID searchId,
                @Param("contentHash") String contentHash,
                @Param("rowCount") int rowCount,
                @Param("ingestedAt") OffsetDateTime ingestedAt);
}

package com.spectrumai.backend.export.bigquery.model;

import com.spectrumai.backend.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registro de controle do que já foi enviado ao BigQuery, uma linha por pesquisa.
 *
 * <p>Mesmo princípio de {@link com.spectrumai.backend.export.model.DataExport}: o
 * {@code contentHash} responde se o conteúdo mudou desde a última carga. A diferença
 * é o que está sendo protegido — lá, um upload redundante ao bucket, que apenas
 * sobrescreveria o objeto; aqui, linhas duplicadas numa tabela append-only, que
 * ficariam visíveis para quem consulta.
 *
 * <p>Não guarda {@code created_by} de propósito: a carga também acontece
 * automaticamente ao fim de uma pesquisa, fora de qualquer requisição, e não haveria
 * usuário a quem atribuir.
 */
@Entity
@Table(name = "bigquery_syncs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BigQuerySync implements TenantAware {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "search_id", nullable = false)
    private UUID searchId;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "ingested_at", nullable = false)
    private OffsetDateTime ingestedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Override
    public UUID getTenantId() {
        return tenantId;
    }
}

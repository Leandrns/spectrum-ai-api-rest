package com.spectrumai.backend.export.model;

import com.spectrumai.backend.export.ExportFormat;
import com.spectrumai.backend.export.ExportScope;
import com.spectrumai.backend.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Registro de controle de um arquivo exportado que já está no bucket.
 *
 * <p>Existe uma linha por (tenant, escopo, recurso, formato). O {@code contentHash}
 * é o que permite reaproveitar o objeto já enviado: se o CSV recém-gerado tem o
 * mesmo hash, os dados de origem não mudaram e basta assinar uma URL nova.
 */
@Entity
@Table(name = "data_exports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataExport implements TenantAware {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExportScope scope;

    /** {@code search_id} ou {@code session_id}, conforme o {@link #scope}. */
    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private ExportFormat format;

    @Column(name = "object_name", nullable = false)
    private String objectName;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

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

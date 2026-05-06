package com.spectrumai.backend.search.model;

import com.spectrumai.backend.company.model.Company;
import com.spectrumai.backend.session.model.AnalysisSession;
import com.spectrumai.backend.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "searches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Search {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    /**
     * Tenant da pesquisa. A coluna é {@code tenant_id} (alinhada ao contrato de API)
     * e referencia {@code companies(id)}.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Company tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private AnalysisSession session;

    @Column(nullable = false, length = 100)
    private String brand;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(length = 100)
    private String trim;

    @Column(name = "model_year")
    private Short year;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]", nullable = false)
    private String[] categories;

    /**
     * Specs estruturadas no schema JSON do contrato (engine, dimensions, technology, ...).
     * Cada campo possui {@code value}, {@code source} e {@code sourceUrl}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String specs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SearchStatus status;

    /** Score 0.00 a 1.00 — média ponderada da confiança por campo. */
    @Column(precision = 3, scale = 2)
    private BigDecimal confidence;

    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}

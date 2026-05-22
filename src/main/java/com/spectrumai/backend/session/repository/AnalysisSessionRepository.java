package com.spectrumai.backend.session.repository;

import com.spectrumai.backend.session.model.AnalysisSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AnalysisSessionRepository extends JpaRepository<AnalysisSession, UUID> {

    // `tenant` em AnalysisSession é ManyToOne — underscore navega para a propriedade `id`.
    Page<AnalysisSession> findByTenant_IdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}

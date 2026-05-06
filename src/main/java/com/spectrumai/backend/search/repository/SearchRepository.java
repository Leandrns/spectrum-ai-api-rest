package com.spectrumai.backend.search.repository;

import com.spectrumai.backend.search.model.Search;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SearchRepository extends JpaRepository<Search, UUID> {

    Page<Search> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<Search> findByTenantIdAndSessionIdOrderByCreatedAtDesc(UUID tenantId, UUID sessionId, Pageable pageable);

    List<Search> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}

package com.spectrumai.backend.search.repository;

import com.spectrumai.backend.search.model.Search;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SearchRepository extends JpaRepository<Search, UUID> {

    // `tenant` e `session` em Search são ManyToOne — o underscore instrui o Spring
    // Data a navegar para a propriedade `id` em vez de procurar um atributo direto
    // `tenantId` (que não existe como campo JPA, só como getter de TenantAware).
    Page<Search> findByTenant_IdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<Search> findByTenant_IdAndSession_IdOrderByCreatedAtDesc(UUID tenantId, UUID sessionId, Pageable pageable);

    List<Search> findBySession_IdOrderByCreatedAtAsc(UUID sessionId);
}

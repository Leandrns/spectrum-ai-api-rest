package com.spectrumai.backend.export.bigquery.repository;

import com.spectrumai.backend.export.bigquery.model.BigQuerySync;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BigQuerySyncRepository extends JpaRepository<BigQuerySync, UUID> {

    /** Busca pela chave única da tabela — o registro da última carga da pesquisa. */
    Optional<BigQuerySync> findByTenantIdAndSearchId(UUID tenantId, UUID searchId);
}

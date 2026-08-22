package com.spectrumai.backend.export.repository;

import com.spectrumai.backend.export.ExportFormat;
import com.spectrumai.backend.export.ExportScope;
import com.spectrumai.backend.export.model.DataExport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DataExportRepository extends JpaRepository<DataExport, UUID> {

    /** Busca pela chave única da tabela — o registro de cache do recurso. */
    Optional<DataExport> findByTenantIdAndScopeAndResourceIdAndFormat(
            UUID tenantId, ExportScope scope, UUID resourceId, ExportFormat format);
}

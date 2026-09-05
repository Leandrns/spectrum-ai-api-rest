package com.spectrumai.backend.search.service;

import com.spectrumai.backend.audit.AuditAction;
import com.spectrumai.backend.audit.AuditService;
import com.spectrumai.backend.auth.security.SecurityUtil;
import com.spectrumai.backend.common.exception.ResourceNotFoundException;
import com.spectrumai.backend.company.model.Company;
import com.spectrumai.backend.company.repository.CompanyRepository;
import com.spectrumai.backend.export.ExportFormat;
import com.spectrumai.backend.export.bigquery.dto.BigQuerySyncResponse;
import com.spectrumai.backend.export.bigquery.service.BigQueryExportService;
import com.spectrumai.backend.export.service.ExportService;
import com.spectrumai.backend.search.dto.SearchEnqueuedResponse;
import com.spectrumai.backend.search.dto.SearchExportResponse;
import com.spectrumai.backend.search.dto.SearchProgressEvent;
import com.spectrumai.backend.search.dto.SearchRequest;
import com.spectrumai.backend.search.dto.SearchResultResponse;
import com.spectrumai.backend.search.dto.SearchSummary;
import com.spectrumai.backend.search.dto.VehicleSummary;
import com.spectrumai.backend.search.model.Search;
import com.spectrumai.backend.search.model.SearchStatus;
import com.spectrumai.backend.search.repository.SearchRepository;
import com.spectrumai.backend.session.model.AnalysisSession;
import com.spectrumai.backend.session.repository.AnalysisSessionRepository;
import com.spectrumai.backend.tenant.TenantContext;
import com.spectrumai.backend.user.model.User;
import com.spectrumai.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SearchServiceImpl implements SearchService {

    private static final int DEFAULT_ESTIMATED_SECONDS = 30;

    private final SearchRepository searchRepository;
    private final AnalysisSessionRepository sessionRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final SearchProcessor searchProcessor;
    private final SearchStreamService streamService;
    private final AuditService auditService;
    private final ExportService exportService;
    private final BigQueryExportService bigQueryExportService;

    @Override
    public SearchEnqueuedResponse enqueue(SearchRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = SecurityUtil.currentUserId();

        AnalysisSession session = null;
        if (request.sessionId() != null) {
            session = sessionRepository.findById(request.sessionId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Sessão não encontrada: " + request.sessionId()));
            if (!tenantId.equals(session.getTenant().getId())) {
                throw new ResourceNotFoundException("Sessão não encontrada: " + request.sessionId());
            }
        }

        Company tenant = companyRepository.getReferenceById(tenantId);
        User user = userRepository.getReferenceById(userId);

        Search search = Search.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .requestedBy(user)
                .session(session)
                .brand(request.brand())
                .model(request.model())
                .trim(request.trim())
                .year(request.year() == null ? null : request.year().shortValue())
                .categories(request.categories().toArray(new String[0]))
                .status(SearchStatus.QUEUED)
                .build();

        Search saved = searchRepository.save(search);
        log.info("Pesquisa enfileirada: id={} tenant={} brand={} model={}",
                saved.getId(), tenantId, saved.getBrand(), saved.getModel());
        auditService.recordSuccess(AuditAction.SEARCH_CREATED, "search", saved.getId().toString(),
                Map.of("brand", saved.getBrand(), "model", saved.getModel()));

        // Dispara o processamento via Gemini somente após o commit, para que a
        // thread async não tente carregar uma pesquisa ainda invisível ao banco.
        UUID searchId = saved.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                streamService.publish(SearchProgressEvent.queued(searchId));
                searchProcessor.process(searchId, tenantId);
            }
        });

        return new SearchEnqueuedResponse(saved.getId(), saved.getStatus(), DEFAULT_ESTIMATED_SECONDS);
    }

    @Override
    @Transactional(readOnly = true)
    public SearchResultResponse getResult(UUID searchId) {
        Search search = loadSearchScoped(searchId);
        return new SearchResultResponse(
                search.getId(),
                toVehicleSummary(search),
                search.getStatus(),
                search.getCompletedAt(),
                search.getSpecs(),
                search.getConfidence(),
                search.getAiLatencyMs()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SearchSummary> history(UUID sessionId, Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();
        Page<Search> page = sessionId == null
                ? searchRepository.findByTenant_IdOrderByCreatedAtDesc(tenantId, pageable)
                : searchRepository.findByTenant_IdAndSession_IdOrderByCreatedAtDesc(tenantId, sessionId, pageable);
        return page.map(s -> new SearchSummary(s.getId(), toVehicleSummary(s), s.getStatus(), s.getCompletedAt()));
    }

    /**
     * Resolve a pesquisa dentro do tenant e delega a geração do arquivo. Não é
     * {@code readOnly}: a exportação registra o arquivo gerado em {@code data_exports}.
     */
    @Override
    public SearchExportResponse export(UUID searchId, ExportFormat format) {
        Search search = loadSearchScoped(searchId);
        return exportService.exportSearch(search, format);
    }

    /**
     * Mesma fronteira de tenant da exportação em arquivo — resolve a pesquisa e
     * delega a carga.
     */
    @Override
    public BigQuerySyncResponse syncToBigQuery(UUID searchId) {
        Search search = loadSearchScoped(searchId);
        return bigQueryExportService.syncSearch(search);
    }

    private Search loadSearchScoped(UUID searchId) {
        UUID tenantId = TenantContext.requireTenantId();
        Search search = searchRepository.findById(searchId)
                .orElseThrow(() -> new ResourceNotFoundException("Pesquisa não encontrada: " + searchId));
        if (!tenantId.equals(search.getTenant().getId())) {
            throw new ResourceNotFoundException("Pesquisa não encontrada: " + searchId);
        }
        return search;
    }

    private VehicleSummary toVehicleSummary(Search s) {
        return new VehicleSummary(
                s.getBrand(),
                s.getModel(),
                s.getTrim(),
                s.getYear() == null ? null : s.getYear().intValue()
        );
    }

}

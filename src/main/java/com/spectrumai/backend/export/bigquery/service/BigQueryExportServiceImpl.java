package com.spectrumai.backend.export.bigquery.service;

import com.spectrumai.backend.audit.AuditAction;
import com.spectrumai.backend.audit.AuditService;
import com.spectrumai.backend.common.exception.BusinessException;
import com.spectrumai.backend.common.exception.ErrorCode;
import com.spectrumai.backend.common.util.ContentHash;
import com.spectrumai.backend.config.AppProperties;
import com.spectrumai.backend.export.bigquery.BigQuerySink;
import com.spectrumai.backend.export.bigquery.VehicleSpecFactMapper;
import com.spectrumai.backend.export.bigquery.dto.BigQuerySyncResponse;
import com.spectrumai.backend.export.bigquery.dto.SessionBigQuerySyncResponse;
import com.spectrumai.backend.export.bigquery.dto.VehicleSpecFact;
import com.spectrumai.backend.export.bigquery.model.BigQuerySync;
import com.spectrumai.backend.export.bigquery.repository.BigQuerySyncRepository;
import com.spectrumai.backend.export.dto.VehicleSpecRow;
import com.spectrumai.backend.export.spec.SpecsFlattener;
import com.spectrumai.backend.search.model.Search;
import com.spectrumai.backend.search.model.SearchStatus;
import com.spectrumai.backend.search.repository.SearchRepository;
import com.spectrumai.backend.session.model.AnalysisSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ingestão idempotente das fichas técnicas no BigQuery.
 *
 * <p>Nenhum método é {@code @Transactional}, ao contrário do resto dos serviços. São
 * duas razões:
 *
 * <ul>
 *   <li>Não há invariante a proteger: cada pesquisa termina numa única escrita, o
 *       registro de controle, feita depois de o BigQuery confirmar as linhas.</li>
 *   <li>{@link #syncCompletedSearchQuietly(UUID)} engole exceções por contrato. Numa
 *       transação, engolir uma falha não a desfaz — a transação já ficou marcada
 *       para rollback e o commit estouraria um {@code UnexpectedRollbackException}
 *       justamente no caminho que não pode falhar.</li>
 * </ul>
 *
 * <p>O tenant vem sempre da pesquisa, e não do {@code TenantContext}: a carga
 * automática roda fora de qualquer requisição.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BigQueryExportServiceImpl implements BigQueryExportService {

    /** Separador entre linhas no cálculo do hash de conteúdo (<em>record separator</em>). */
    private static final String RECORD_SEPARATOR = Character.toString(0x1E);

    private static final int DEFAULT_BATCH_SIZE = 500;

    private final SearchRepository searchRepository;
    private final BigQuerySyncRepository syncRepository;
    private final SpecsFlattener flattener;
    private final VehicleSpecFactMapper mapper;
    private final BigQuerySink sink;
    private final AuditService auditService;
    private final AppProperties properties;

    @Override
    public BigQuerySyncResponse syncSearch(Search search) {
        requireEnabled();
        if (search.getStatus() != SearchStatus.COMPLETED) {
            throw new BusinessException(
                    "A pesquisa ainda não foi concluída (status " + search.getStatus() + ").",
                    HttpStatus.CONFLICT,
                    ErrorCode.VALIDATION_ERROR);
        }

        List<VehicleSpecRow> rows = flattener.flatten(search);
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "A pesquisa não possui especificações para exportar.",
                    HttpStatus.CONFLICT,
                    ErrorCode.VALIDATION_ERROR);
        }

        SyncOutcome outcome = ingest(search, rows, OffsetDateTime.now());

        auditService.recordSuccess(AuditAction.SEARCH_BQ_SYNCED, "search", search.getId().toString(),
                Map.of("rows", outcome.rows(), "skipped", outcome.skipped()));

        return new BigQuerySyncResponse(search.getId(), outcome.rows(), outcome.skipped(),
                outcome.ingestedAt());
    }

    @Override
    public SessionBigQuerySyncResponse syncSession(AnalysisSession session) {
        requireEnabled();
        List<Search> searches = searchRepository
                .findByTenant_IdAndSession_IdAndStatusOrderByCompletedAtDesc(
                        session.getTenantId(), session.getId(), SearchStatus.COMPLETED);
        if (searches.isEmpty()) {
            throw new BusinessException(
                    "A sessão não possui pesquisas concluídas para exportar.",
                    HttpStatus.CONFLICT,
                    ErrorCode.VALIDATION_ERROR);
        }

        // Sem deduplicação por veículo, ao contrário da exportação em arquivo: aqui
        // cada linha carrega seu search_id, então pesquisas repetidas do mesmo veículo
        // convivem na tabela e quem escolhe entre elas é a consulta.
        OffsetDateTime ingestedAt = OffsetDateTime.now();
        int synced = 0;
        int skipped = 0;
        int rows = 0;
        for (Search search : searches) {
            List<VehicleSpecRow> specRows = flattener.flatten(search);
            if (specRows.isEmpty()) {
                log.warn("Pesquisa {} da sessão {} não tem especificações — ignorada na carga",
                        search.getId(), session.getId());
                skipped++;
                continue;
            }
            SyncOutcome outcome = ingest(search, specRows, ingestedAt);
            if (outcome.skipped()) {
                skipped++;
            } else {
                synced++;
                rows += outcome.rows();
            }
        }

        auditService.recordSuccess(AuditAction.SESSION_BQ_SYNCED, "session", session.getId().toString(),
                Map.of("searches", searches.size(), "synced", synced, "skipped", skipped, "rows", rows));

        return new SessionBigQuerySyncResponse(session.getId(), searches.size(), synced, skipped,
                rows, ingestedAt);
    }

    @Override
    public void syncCompletedSearchQuietly(UUID searchId) {
        if (!enabled() || !properties.bigquery().autoSync()) {
            return;
        }
        try {
            Search search = searchRepository.findById(searchId).orElse(null);
            if (search == null || search.getStatus() != SearchStatus.COMPLETED) {
                return;
            }
            List<VehicleSpecRow> rows = flattener.flatten(search);
            if (rows.isEmpty()) {
                log.warn("Pesquisa {} concluída sem especificações — nada a enviar ao BigQuery", searchId);
                return;
            }
            SyncOutcome outcome = ingest(search, rows, OffsetDateTime.now());
            log.info("Pesquisa {} sincronizada com o BigQuery: {} linhas (skipped={})",
                    searchId, outcome.rows(), outcome.skipped());
        } catch (Exception e) {
            // A pesquisa foi respondida com sucesso pela IA; uma falha na ingestão não
            // pode alterar esse resultado. Fica pendente até alguém chamar o endpoint.
            log.error("Falha ao sincronizar a pesquisa {} com o BigQuery — a pesquisa segue válida",
                    searchId, e);
        }
    }

    /**
     * Envia as linhas quando o conteúdo mudou e atualiza o registro de controle.
     *
     * <p>O hash é calculado sobre o conteúdo sem o carimbo de ingestão: incluí-lo
     * faria o hash mudar a cada execução e a guarda nunca pegaria nada.
     */
    private SyncOutcome ingest(Search search, List<VehicleSpecRow> rows, OffsetDateTime ingestedAt) {
        UUID tenantId = search.getTenantId();
        List<VehicleSpecFact> facts = mapper.toFacts(search, rows, ingestedAt);
        String hash = contentHash(facts);

        Optional<BigQuerySync> control = syncRepository
                .findByTenantIdAndSearchId(tenantId, search.getId());
        if (control.isPresent() && hash.equals(control.get().getContentHash())) {
            BigQuerySync existing = control.get();
            log.debug("Pesquisa {} inalterada desde a carga de {} — nada enviado",
                    search.getId(), existing.getIngestedAt());
            return new SyncOutcome(existing.getRowCount(), true, existing.getIngestedAt());
        }

        sink.ensureSchema();
        for (List<VehicleSpecFact> batch : batches(facts)) {
            sink.insertAll(batch);
        }
        // O id só é usado quando não há registro ainda; no conflito, o upsert atualiza
        // a linha existente e descarta este valor.
        UUID controlId = control.map(BigQuerySync::getId).orElseGet(UUID::randomUUID);
        syncRepository.upsert(controlId, tenantId, search.getId(), hash, facts.size(), ingestedAt);

        return new SyncOutcome(facts.size(), false, ingestedAt);
    }

    private String contentHash(List<VehicleSpecFact> facts) {
        StringBuilder content = new StringBuilder();
        for (VehicleSpecFact fact : facts) {
            content.append(fact.fingerprint()).append(RECORD_SEPARATOR);
        }
        return ContentHash.of(content.toString());
    }

    /** Fatia as linhas no tamanho de lote suportado por uma requisição do BigQuery. */
    private List<List<VehicleSpecFact>> batches(List<VehicleSpecFact> facts) {
        int size = properties.bigquery().batchSize();
        if (size <= 0) {
            size = DEFAULT_BATCH_SIZE;
        }
        List<List<VehicleSpecFact>> batches = new ArrayList<>();
        for (int start = 0; start < facts.size(); start += size) {
            batches.add(facts.subList(start, Math.min(start + size, facts.size())));
        }
        return batches;
    }

    private void requireEnabled() {
        if (!enabled()) {
            throw new BusinessException(
                    "Integração com o BigQuery não está habilitada (spectrum.bigquery.enabled=false).",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ErrorCode.WAREHOUSE_ERROR);
        }
    }

    private boolean enabled() {
        return properties.bigquery() != null && properties.bigquery().enabled();
    }

    /** Resultado de uma pesquisa dentro da carga. */
    private record SyncOutcome(int rows, boolean skipped, OffsetDateTime ingestedAt) {}
}

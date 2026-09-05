package com.spectrumai.backend.export.bigquery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrumai.backend.audit.AuditService;
import com.spectrumai.backend.common.exception.BusinessException;
import com.spectrumai.backend.company.model.Company;
import com.spectrumai.backend.config.AppProperties;
import com.spectrumai.backend.export.bigquery.BigQuerySink;
import com.spectrumai.backend.export.bigquery.VehicleSpecFactMapper;
import com.spectrumai.backend.export.bigquery.dto.BigQuerySyncResponse;
import com.spectrumai.backend.export.bigquery.dto.SessionBigQuerySyncResponse;
import com.spectrumai.backend.export.bigquery.dto.VehicleSpecFact;
import com.spectrumai.backend.export.bigquery.model.BigQuerySync;
import com.spectrumai.backend.export.bigquery.repository.BigQuerySyncRepository;
import com.spectrumai.backend.export.spec.SpecsFlattener;
import com.spectrumai.backend.search.model.Search;
import com.spectrumai.backend.search.model.SearchStatus;
import com.spectrumai.backend.search.repository.SearchRepository;
import com.spectrumai.backend.session.model.AnalysisSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BigQueryExportServiceImplTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    private SearchRepository searchRepository;
    private BigQuerySyncRepository syncRepository;
    private BigQuerySink sink;
    private AuditService auditService;
    private BigQueryExportServiceImpl service;

    @BeforeEach
    void setUp() {
        searchRepository = mock(SearchRepository.class);
        syncRepository = mock(BigQuerySyncRepository.class);
        sink = mock(BigQuerySink.class);
        auditService = mock(AuditService.class);

        service = build(properties(true, true, 500));

        when(syncRepository.findByTenantIdAndSearchId(any(), any())).thenReturn(Optional.empty());
    }

    private BigQueryExportServiceImpl build(AppProperties properties) {
        return new BigQueryExportServiceImpl(
                searchRepository,
                syncRepository,
                new SpecsFlattener(new ObjectMapper()),
                new VehicleSpecFactMapper(),
                sink,
                auditService,
                properties);
    }

    private AppProperties properties(boolean enabled, boolean autoSync, int batchSize) {
        return new AppProperties(null, null, null,
                new AppProperties.BigQuery(enabled, "projeto", "spectrum_analytics", "vehicle_specs",
                        730, autoSync, batchSize, null));
    }

    /** Ficha com {@code fields} campos numa única categoria. */
    private Search search(String brand, int fields, OffsetDateTime completedAt) {
        StringBuilder specs = new StringBuilder("{\"Rodas\": {");
        for (int i = 0; i < fields; i++) {
            specs.append(i == 0 ? "" : ",")
                    .append("\"Campo ").append(i).append("\": ")
                    .append("{\"value\": \"").append(i).append("\", \"source\": \"OFFICIAL\"}");
        }
        specs.append("}}");

        return Search.builder()
                .id(UUID.randomUUID())
                .tenant(Company.builder().id(TENANT_ID).build())
                .brand(brand)
                .model("Corolla")
                .trim("Altis")
                .year((short) 2024)
                .status(SearchStatus.COMPLETED)
                .completedAt(completedAt)
                .specs(specs.toString())
                .build();
    }

    private AnalysisSession session() {
        return AnalysisSession.builder()
                .id(UUID.randomUUID())
                .tenant(Company.builder().id(TENANT_ID).build())
                .name("Comparativo SUVs 2024")
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<List<VehicleSpecFact>> capturedBatches() {
        ArgumentCaptor<List<VehicleSpecFact>> captor = ArgumentCaptor.forClass(List.class);
        verify(sink, org.mockito.Mockito.atLeastOnce()).insertAll(captor.capture());
        return (List<List<VehicleSpecFact>>) (List<?>) captor.getAllValues();
    }

    @Test
    @DisplayName("envia as linhas da pesquisa e registra a carga")
    void syncsSearch() {
        Search search = search("Toyota", 3, OffsetDateTime.now());

        BigQuerySyncResponse response = service.syncSearch(search);

        assertThat(response.searchId()).isEqualTo(search.getId());
        assertThat(response.rows()).isEqualTo(3);
        assertThat(response.skipped()).isFalse();
        assertThat(response.ingestedAt()).isNotNull();

        verify(sink).ensureSchema();
        assertThat(capturedBatches().getFirst()).hasSize(3);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> rows = ArgumentCaptor.forClass(Integer.class);
        verify(syncRepository).upsert(any(), eq(TENANT_ID), eq(search.getId()),
                hash.capture(), rows.capture(), any());
        assertThat(hash.getValue()).hasSize(64);
        assertThat(rows.getValue()).isEqualTo(3);
    }

    /**
     * O tenant sai da pesquisa, e não do {@code TenantContext}: a carga automática
     * roda numa thread sem requisição. Nenhum teste aqui popula o contexto.
     */
    @Test
    @DisplayName("usa o tenant da pesquisa, sem depender do contexto da requisição")
    void takesTenantFromSearch() {
        service.syncSearch(search("Toyota", 1, OffsetDateTime.now()));

        assertThat(capturedBatches().getFirst().getFirst().tenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    @DisplayName("conteúdo inalterado não é reenviado")
    void skipsUnchangedContent() {
        Search search = search("Toyota", 3, OffsetDateTime.now());
        // Primeira carga só para descobrir o hash que o conteúdo produz.
        service.syncSearch(search);
        BigQuerySync registro = controlRecordFromFirstUpsert(search);

        when(syncRepository.findByTenantIdAndSearchId(TENANT_ID, search.getId()))
                .thenReturn(Optional.of(registro));

        BigQuerySyncResponse response = service.syncSearch(search);

        assertThat(response.skipped()).isTrue();
        assertThat(response.rows()).isEqualTo(registro.getRowCount());
        assertThat(response.ingestedAt()).isEqualTo(registro.getIngestedAt());
        // Continua sendo uma única inserção: a segunda chamada não enviou nada.
        verify(sink, times(1)).insertAll(anyList());
        verify(syncRepository, times(1)).upsert(any(), any(), any(), any(), anyInt(), any());
    }

    /** Reconstrói o registro de controle a partir dos argumentos do upsert. */
    private BigQuerySync controlRecordFromFirstUpsert(Search search) {
        ArgumentCaptor<UUID> id = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> rows = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<OffsetDateTime> ingestedAt = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(syncRepository).upsert(id.capture(), eq(TENANT_ID), eq(search.getId()),
                hash.capture(), rows.capture(), ingestedAt.capture());
        return BigQuerySync.builder()
                .id(id.getValue())
                .tenantId(TENANT_ID)
                .searchId(search.getId())
                .contentHash(hash.getValue())
                .rowCount(rows.getValue())
                .ingestedAt(ingestedAt.getValue())
                .build();
    }

    @Test
    @DisplayName("conteúdo alterado reenvia e atualiza o registro existente")
    void reingestsChangedContent() {
        Search search = search("Toyota", 2, OffsetDateTime.now());
        BigQuerySync registro = BigQuerySync.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .searchId(search.getId())
                .contentHash("hash-que-nao-bate")
                .rowCount(2)
                .ingestedAt(OffsetDateTime.now().minusDays(1))
                .build();
        when(syncRepository.findByTenantIdAndSearchId(any(), any())).thenReturn(Optional.of(registro));

        BigQuerySyncResponse response = service.syncSearch(search);

        assertThat(response.skipped()).isFalse();
        verify(sink).insertAll(anyList());
        // Reaproveita o id do registro existente em vez de tentar criar outra linha.
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(syncRepository).upsert(eq(registro.getId()), eq(TENANT_ID), eq(search.getId()),
                hash.capture(), anyInt(), any());
        assertThat(hash.getValue()).isNotEqualTo("hash-que-nao-bate");
    }

    @Test
    @DisplayName("quebra em lotes do tamanho configurado")
    void splitsIntoBatches() {
        service = build(properties(true, true, 2));

        service.syncSearch(search("Toyota", 5, OffsetDateTime.now()));

        List<List<VehicleSpecFact>> batches = capturedBatches();
        assertThat(batches).hasSize(3);
        assertThat(batches.get(0)).hasSize(2);
        assertThat(batches.get(1)).hasSize(2);
        assertThat(batches.get(2)).hasSize(1);
    }

    @Test
    @DisplayName("recusa pesquisa que ainda não terminou")
    void rejectsUnfinishedSearch() {
        Search search = search("Toyota", 1, null);
        search.setStatus(SearchStatus.PROCESSING);

        assertThatThrownBy(() -> service.syncSearch(search))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(sink, never()).insertAll(anyList());
    }

    @Test
    @DisplayName("recusa pesquisa concluída sem especificações")
    void rejectsSearchWithoutSpecs() {
        Search search = search("Toyota", 1, OffsetDateTime.now());
        search.setSpecs(null);

        assertThatThrownBy(() -> service.syncSearch(search))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("com a integração desligada, responde 503 sem tocar no BigQuery")
    void rejectsWhenDisabled() {
        service = build(properties(false, false, 500));

        assertThatThrownBy(() -> service.syncSearch(search("Toyota", 1, OffsetDateTime.now())))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(sink, never()).ensureSchema();
    }

    /**
     * Ao contrário do CSV, o mesmo veículo pesquisado duas vezes entra duas vezes:
     * as linhas se distinguem pelo {@code search_id} e a view resolve a revisão.
     */
    @Test
    @DisplayName("na sessão, veículo repetido entra uma vez por pesquisa")
    void sessionKeepsEverySearch() {
        AnalysisSession session = session();
        OffsetDateTime agora = OffsetDateTime.now();
        when(searchRepository.findByTenant_IdAndSession_IdAndStatusOrderByCompletedAtDesc(
                eq(TENANT_ID), eq(session.getId()), eq(SearchStatus.COMPLETED)))
                .thenReturn(List.of(
                        search("Toyota", 2, agora),
                        search("Toyota", 2, agora.minusDays(3)),
                        search("Fiat", 3, agora.minusHours(2))));

        SessionBigQuerySyncResponse response = service.syncSession(session);

        assertThat(response.searches()).isEqualTo(3);
        assertThat(response.synced()).isEqualTo(3);
        assertThat(response.skipped()).isZero();
        assertThat(response.rows()).isEqualTo(7);
    }

    @Test
    @DisplayName("na sessão, pesquisa sem especificações é ignorada e não derruba a carga")
    void sessionSkipsSearchWithoutSpecs() {
        AnalysisSession session = session();
        Search semSpecs = search("Fiat", 1, OffsetDateTime.now());
        semSpecs.setSpecs("isso não é json");
        when(searchRepository.findByTenant_IdAndSession_IdAndStatusOrderByCompletedAtDesc(
                any(), any(), any()))
                .thenReturn(List.of(search("Toyota", 2, OffsetDateTime.now()), semSpecs));

        SessionBigQuerySyncResponse response = service.syncSession(session);

        assertThat(response.synced()).isEqualTo(1);
        assertThat(response.skipped()).isEqualTo(1);
        assertThat(response.rows()).isEqualTo(2);
    }

    @Test
    @DisplayName("recusa sessão sem nenhuma pesquisa concluída")
    void rejectsEmptySession() {
        when(searchRepository.findByTenant_IdAndSession_IdAndStatusOrderByCompletedAtDesc(
                any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.syncSession(session()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("o gancho automático engole a falha do BigQuery")
    void quietSyncSwallowsFailure() {
        Search search = search("Toyota", 2, OffsetDateTime.now());
        when(searchRepository.findById(search.getId())).thenReturn(Optional.of(search));
        org.mockito.Mockito.doThrow(new BusinessException("BigQuery fora do ar",
                HttpStatus.BAD_GATEWAY, "WAREHOUSE_ERROR")).when(sink).insertAll(anyList());

        service.syncCompletedSearchQuietly(search.getId());

        verify(syncRepository, never()).upsert(any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("o gancho automático não roda com auto-sync desligado")
    void quietSyncRespectsFlag() {
        service = build(properties(true, false, 500));
        Search search = search("Toyota", 2, OffsetDateTime.now());

        service.syncCompletedSearchQuietly(search.getId());

        verify(searchRepository, never()).findById(any());
        verify(sink, never()).insertAll(anyList());
    }

    @Test
    @DisplayName("o gancho automático ignora pesquisa que não está concluída")
    void quietSyncIgnoresUnfinishedSearch() {
        Search search = search("Toyota", 2, null);
        search.setStatus(SearchStatus.FAILED);
        when(searchRepository.findById(search.getId())).thenReturn(Optional.of(search));

        service.syncCompletedSearchQuietly(search.getId());

        verify(sink, never()).insertAll(anyList());
    }
}

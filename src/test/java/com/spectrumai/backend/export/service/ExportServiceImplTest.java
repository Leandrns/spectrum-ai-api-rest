package com.spectrumai.backend.export.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrumai.backend.audit.AuditService;
import com.spectrumai.backend.auth.security.UserPrincipal;
import com.spectrumai.backend.common.exception.BusinessException;
import com.spectrumai.backend.company.model.Company;
import com.spectrumai.backend.config.AppProperties;
import com.spectrumai.backend.export.ExportFormat;
import com.spectrumai.backend.export.ExportScope;
import com.spectrumai.backend.export.model.DataExport;
import com.spectrumai.backend.export.repository.DataExportRepository;
import com.spectrumai.backend.export.spec.SpecsFlattener;
import com.spectrumai.backend.export.storage.ExportStorage;
import com.spectrumai.backend.export.writer.CsvExportWriter;
import com.spectrumai.backend.export.writer.ExportWriter;
import com.spectrumai.backend.export.writer.ExportWriterResolver;
import com.spectrumai.backend.search.dto.SearchExportResponse;
import com.spectrumai.backend.search.model.Search;
import com.spectrumai.backend.search.model.SearchStatus;
import com.spectrumai.backend.search.repository.SearchRepository;
import com.spectrumai.backend.session.model.AnalysisSession;
import com.spectrumai.backend.tenant.TenantContext;
import com.spectrumai.backend.user.model.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportServiceImplTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private SearchRepository searchRepository;
    private DataExportRepository exportRepository;
    private ExportStorage storage;
    private AuditService auditService;
    private ExportServiceImpl service;

    @BeforeEach
    void setUp() {
        searchRepository = mock(SearchRepository.class);
        exportRepository = mock(DataExportRepository.class);
        storage = mock(ExportStorage.class);
        auditService = mock(AuditService.class);

        AppProperties properties = new AppProperties(null, null,
                new AppProperties.Storage(
                        new AppProperties.Storage.Gcs("bucket-de-teste", "projeto", 60, "exports", null)));

        ExportWriter pdfWriter = mock(ExportWriter.class);
        when(pdfWriter.format()).thenReturn(ExportFormat.PDF);
        when(pdfWriter.write(any())).thenReturn("%PDF-1.4-sample".getBytes(StandardCharsets.UTF_8));

        service = new ExportServiceImpl(
                searchRepository,
                exportRepository,
                new SpecsFlattener(new ObjectMapper()),
                new ExportWriterResolver(List.of(new CsvExportWriter(), pdfWriter)),
                storage,
                auditService,
                properties);

        when(storage.signedUrl(anyString(), any())).thenReturn("https://storage.googleapis.com/assinada");
        when(exportRepository.findByTenantIdAndScopeAndResourceIdAndFormat(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        TenantContext.setTenantId(TENANT_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(USER_ID, TENANT_ID, "analista@spectrum.ai", Role.ANALYST),
                        null, List.of()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private Search search(String brand, String model, String trim, Short year,
                          OffsetDateTime completedAt, String value) {
        Company tenant = Company.builder().id(TENANT_ID).build();
        return Search.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .brand(brand)
                .model(model)
                .trim(trim)
                .year(year)
                .status(SearchStatus.COMPLETED)
                .completedAt(completedAt)
                .specs("{\"Rodas\": {\"Aro (polegadas)\": {\"value\": \"" + value + "\", \"source\": \"OFFICIAL\"}}}")
                .build();
    }

    private AnalysisSession session() {
        return AnalysisSession.builder()
                .id(UUID.randomUUID())
                .tenant(Company.builder().id(TENANT_ID).build())
                .name("Comparativo SUVs 2024")
                .build();
    }

    /** Recupera o conteúdo que o serviço mandou para o bucket. */
    private String uploadedCsv() {
        ArgumentCaptor<byte[]> content = ArgumentCaptor.forClass(byte[].class);
        verify(storage).upload(anyString(), content.capture(), anyString(), anyString());
        byte[] bytes = content.getValue();
        return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("exporta a pesquisa e devolve a URL assinada com validade")
    void exportsSearch() {
        Search search = search("Toyota", "Corolla", "Altis", (short) 2024, OffsetDateTime.now(), "17");

        SearchExportResponse response = service.exportSearch(search, ExportFormat.CSV);

        assertThat(response.downloadUrl()).isEqualTo("https://storage.googleapis.com/assinada");
        assertThat(response.expiresAt()).isAfter(OffsetDateTime.now().plusMinutes(59));
        assertThat(uploadedCsv()).contains("Toyota,Corolla,Altis,2024,Rodas,Aro (polegadas),17,OFFICIAL");
    }

    @Test
    @DisplayName("o objeto vai para uma pasta do tenant, isolando os arquivos por empresa")
    void objectNameIsScopedToTenant() {
        service.exportSearch(search("Fiat", "Toro", "Ranch", (short) 2024, OffsetDateTime.now(), "18"),
                ExportFormat.CSV);

        ArgumentCaptor<String> objectName = ArgumentCaptor.forClass(String.class);
        verify(storage).upload(objectName.capture(), any(), anyString(), anyString());
        assertThat(objectName.getValue())
                .startsWith("exports/" + TENANT_ID + "/search/")
                .endsWith(".csv");
    }

    @Test
    @DisplayName("recusa exportar pesquisa que ainda não terminou")
    void rejectsUnfinishedSearch() {
        Search search = search("Toyota", "Corolla", "Altis", (short) 2024, null, "17");
        search.setStatus(SearchStatus.PROCESSING);

        assertThatThrownBy(() -> service.exportSearch(search, ExportFormat.CSV))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(storage, never()).upload(anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("exporta a pesquisa em PDF e faz o upload com content-type application/pdf")
    void exportsSearchInPdf() {
        Search search = search("Toyota", "Corolla", "Altis", (short) 2024, OffsetDateTime.now(), "17");

        SearchExportResponse response = service.exportSearch(search, ExportFormat.PDF);

        assertThat(response.downloadUrl()).isEqualTo("https://storage.googleapis.com/assinada");
        assertThat(response.expiresAt()).isAfter(OffsetDateTime.now().plusMinutes(59));

        ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> filenameCaptor = ArgumentCaptor.forClass(String.class);
        verify(storage).upload(anyString(), any(), contentTypeCaptor.capture(), filenameCaptor.capture());

        assertThat(contentTypeCaptor.getValue()).isEqualTo("application/pdf");
        assertThat(filenameCaptor.getValue()).isEqualTo("spectrum_toyota_corolla_2024.pdf");
    }

    @Test
    @DisplayName("na sessão, veículo repetido entra só uma vez — vale a pesquisa mais recente")
    void sessionKeepsOnlyLatestSearchPerVehicle() {
        OffsetDateTime agora = OffsetDateTime.now();
        AnalysisSession session = session();
        // A query real ordena por completed_at desc; o mock reproduz essa ordem.
        when(searchRepository.findByTenant_IdAndSession_IdAndStatusOrderByCompletedAtDesc(
                eq(TENANT_ID), eq(session.getId()), eq(SearchStatus.COMPLETED)))
                .thenReturn(List.of(
                        search("Toyota", "Corolla", "Altis", (short) 2024, agora, "17"),
                        search("Toyota", "Corolla", "Altis", (short) 2024, agora.minusDays(3), "15"),
                        search("Fiat", "Toro", "Ranch", (short) 2024, agora.minusHours(2), "18")));

        service.exportSession(session, ExportFormat.CSV);

        String csv = uploadedCsv();
        assertThat(csv.lines()).hasSize(3); // cabeçalho + 2 veículos
        assertThat(csv).contains("Toyota,Corolla,Altis,2024,Rodas,Aro (polegadas),17,OFFICIAL");
        assertThat(csv).doesNotContain(",15,OFFICIAL");
        // Ordenação alfabética por marca: Fiat antes de Toyota.
        assertThat(csv.indexOf("Fiat")).isLessThan(csv.indexOf("Toyota"));
    }

    @Test
    @DisplayName("arquivo completo de uma ficha realista, do JSON do Gemini ao CSV final")
    void producesExpectedCsvForRealisticSpecs() {
        Search search = search("Toyota", "Corolla Cross", "XRE", (short) 2026, OffsetDateTime.now(), "ignorado");
        search.setSpecs("""
                {
                  "Motor e Transmissão": {
                    "Potência": {"value": "177 cv", "source": "OFFICIAL"},
                    "Torque": {"value": "21,0 kgfm", "source": "REVIEW"},
                    "Economia de Combustível": {"value": "Dado não encontrado", "source": "NOT_FOUND"}
                  },
                  "Rodas": {
                    "Aro (polegadas)": {"value": "18", "source": "OFFICIAL"},
                    "Pneus Run-Flat": {"value": "Não, equipamento \\"padrão\\" apenas", "source": "REVIEW"}
                  },
                  "sources": {"1": {"value": "https://toyota.com.br", "source": "OFFICIAL"}}
                }""");

        service.exportSearch(search, ExportFormat.CSV);

        assertThat(uploadedCsv()).isEqualTo("""
                marca,modelo,versao,ano_modelo,categoria,campo,valor,fonte\r
                Toyota,Corolla Cross,XRE,2026,Motor e Transmissão,Potência,177 cv,OFFICIAL\r
                Toyota,Corolla Cross,XRE,2026,Motor e Transmissão,Torque,"21,0 kgfm",REVIEW\r
                Toyota,Corolla Cross,XRE,2026,Motor e Transmissão,Economia de Combustível,Dado não encontrado,NOT_FOUND\r
                Toyota,Corolla Cross,XRE,2026,Rodas,Aro (polegadas),18,OFFICIAL\r
                Toyota,Corolla Cross,XRE,2026,Rodas,Pneus Run-Flat,"Não, equipamento ""padrão"" apenas",REVIEW\r
                """);
    }

    @Test
    @DisplayName("recusa exportar sessão sem nenhuma pesquisa concluída")
    void rejectsEmptySession() {
        AnalysisSession session = session();
        when(searchRepository.findByTenant_IdAndSession_IdAndStatusOrderByCompletedAtDesc(
                any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.exportSession(session, ExportFormat.CSV))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("conteúdo idêntico reaproveita o objeto do bucket em vez de reenviar")
    void reusesStoredObjectWhenContentIsUnchanged() {
        Search search = search("Toyota", "Corolla", "Altis", (short) 2024, OffsetDateTime.now(), "17");
        // Primeira chamada só para descobrir o hash que o conteúdo produz.
        service.exportSearch(search, ExportFormat.CSV);
        ArgumentCaptor<DataExport> saved = ArgumentCaptor.forClass(DataExport.class);
        verify(exportRepository).save(saved.capture());
        DataExport registro = saved.getValue();

        when(exportRepository.findByTenantIdAndScopeAndResourceIdAndFormat(
                TENANT_ID, ExportScope.SEARCH, search.getId(), ExportFormat.CSV))
                .thenReturn(Optional.of(registro));
        when(storage.exists(registro.getObjectName())).thenReturn(true);

        service.exportSearch(search, ExportFormat.CSV);

        // Continua sendo um único upload: o segundo export reaproveitou o objeto.
        verify(storage).upload(anyString(), any(), anyString(), anyString());
        verify(storage).exists(registro.getObjectName());
    }

    @Test
    @DisplayName("reenvia quando o objeto sumiu do bucket, mesmo com o hash batendo")
    void reuploadsWhenStoredObjectIsGone() {
        Search search = search("Toyota", "Corolla", "Altis", (short) 2024, OffsetDateTime.now(), "17");
        DataExport registro = DataExport.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .scope(ExportScope.SEARCH)
                .resourceId(search.getId())
                .format(ExportFormat.CSV)
                .objectName("exports/" + TENANT_ID + "/search/" + search.getId() + "/deadbeef1234.csv")
                .contentHash("hash-que-nao-bate")
                .build();
        when(exportRepository.findByTenantIdAndScopeAndResourceIdAndFormat(any(), any(), any(), any()))
                .thenReturn(Optional.of(registro));

        service.exportSearch(search, ExportFormat.CSV);

        verify(storage).upload(anyString(), any(), anyString(), anyString());
        // O registro existente é atualizado no lugar de criar uma linha duplicada.
        verify(exportRepository).save(registro);
    }
}

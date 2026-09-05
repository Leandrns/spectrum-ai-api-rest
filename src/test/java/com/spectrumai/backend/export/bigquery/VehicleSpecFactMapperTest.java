package com.spectrumai.backend.export.bigquery;

import com.spectrumai.backend.company.model.Company;
import com.spectrumai.backend.export.bigquery.dto.VehicleSpecFact;
import com.spectrumai.backend.export.dto.VehicleSpecRow;
import com.spectrumai.backend.search.model.Search;
import com.spectrumai.backend.search.model.SearchStatus;
import com.spectrumai.backend.session.model.AnalysisSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleSpecFactMapperTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    private final VehicleSpecFactMapper mapper = new VehicleSpecFactMapper();

    private Search search(OffsetDateTime completedAt, AnalysisSession session) {
        return Search.builder()
                .id(UUID.randomUUID())
                .tenant(Company.builder().id(TENANT_ID).build())
                .session(session)
                .brand("Toyota")
                .model("Corolla Cross")
                .trim("XRE")
                .year((short) 2026)
                .status(SearchStatus.COMPLETED)
                .completedAt(completedAt)
                .confidence(new BigDecimal("0.87"))
                .build();
    }

    private VehicleSpecRow row(String valor, String fonte) {
        return new VehicleSpecRow("Toyota", "Corolla Cross", "XRE", 2026,
                "Motor e Transmissão", "Potência", valor, fonte);
    }

    @Test
    @DisplayName("carrega as chaves analíticas que o arquivo não tem")
    void mapsAnalyticalKeys() {
        AnalysisSession session = AnalysisSession.builder().id(UUID.randomUUID()).build();
        OffsetDateTime concluida = OffsetDateTime.now().minusHours(2);
        OffsetDateTime carga = OffsetDateTime.now();
        Search search = search(concluida, session);

        VehicleSpecFact fact = mapper.toFacts(search, List.of(row("177 cv", "OFFICIAL")), carga)
                .getFirst();

        assertThat(fact.tenantId()).isEqualTo(TENANT_ID);
        assertThat(fact.searchId()).isEqualTo(search.getId());
        assertThat(fact.sessionId()).isEqualTo(session.getId());
        assertThat(fact.confianca()).isEqualByComparingTo("0.87");
        assertThat(fact.pesquisadoEm()).isEqualTo(concluida);
        assertThat(fact.ingeridoEm()).isEqualTo(carga);
    }

    @Test
    @DisplayName("pesquisa fora de sessão não inventa session_id")
    void allowsSearchWithoutSession() {
        VehicleSpecFact fact = mapper
                .toFacts(search(OffsetDateTime.now(), null), List.of(row("177 cv", "OFFICIAL")),
                        OffsetDateTime.now())
                .getFirst();

        assertThat(fact.sessionId()).isNull();
    }

    @Test
    @DisplayName("NOT_FOUND vira encontrado=false, sem número derivado")
    void marksMissingFields() {
        List<VehicleSpecFact> facts = mapper.toFacts(
                search(OffsetDateTime.now(), null),
                List.of(row("177 cv", "OFFICIAL"), row("Dado não encontrado", "NOT_FOUND")),
                OffsetDateTime.now());

        assertThat(facts.get(0).encontrado()).isTrue();
        assertThat(facts.get(0).valorNum()).isEqualTo(177.0);
        assertThat(facts.get(1).encontrado()).isFalse();
        assertThat(facts.get(1).valorNum()).isNull();
    }

    /**
     * Sem {@code completed_at} a linha cairia na partição {@code __NULL__}, fora do
     * alcance de qualquer filtro por data.
     */
    @Test
    @DisplayName("sem completed_at, particiona pela data da carga em vez de nulo")
    void fallsBackToIngestionTimestamp() {
        OffsetDateTime carga = OffsetDateTime.now();

        VehicleSpecFact fact = mapper
                .toFacts(search(null, null), List.of(row("177 cv", "OFFICIAL")), carga)
                .getFirst();

        assertThat(fact.pesquisadoEm()).isEqualTo(carga);
    }

    @ParameterizedTest
    @DisplayName("extrai o primeiro número do valor, na convenção do português")
    @CsvSource(value = {
            "177 cv                          | 177.0",
            "18                              | 18.0",
            "21,0 kgfm                       | 21.0",
            // Ponto seguido de três dígitos é separador de milhar...
            "1.999 cm³                       | 1999.0",
            "150.000,00                      | 150000.0",
            // ...e qualquer outro ponto é decimal, senão o motor 1.6 viraria 16.
            "1.6 Turbo                       | 1.6",
            "7 lugares                       | 7.0",
            "-10 °C                          | -10.0",
            "Sim                             | null",
            "Dado não encontrado             | null",
            "R$ 150.000                      | null",
    }, delimiter = '|', nullValues = "null")
    void parsesLeadingNumber(String valor, Double expected) {
        assertThat(VehicleSpecFactMapper.parseLeadingNumber(valor.trim())).isEqualTo(expected);
    }

    @Test
    @DisplayName("valor nulo ou vazio não quebra o parser")
    void toleratesNullValue() {
        assertThat(VehicleSpecFactMapper.parseLeadingNumber(null)).isNull();
        assertThat(VehicleSpecFactMapper.parseLeadingNumber("")).isNull();
        assertThat(VehicleSpecFactMapper.parseLeadingNumber("   ")).isNull();
    }
}

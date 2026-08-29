package com.spectrumai.backend.export.writer;

import com.spectrumai.backend.export.ExportFormat;
import com.spectrumai.backend.export.dto.VehicleSpecRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfExportWriterTest {

    private PdfExportWriter writer;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCharacterEncoding("UTF-8");

        // SpringTemplateEngine, não o TemplateEngine cru: o engine padrão do
        // Thymeleaf avalia ${...} com OGNL, que não está no classpath (o
        // starter traz SpringEL). Além de fazer o teste rodar, isso o alinha
        // ao bean que o Spring injeta em produção — é a mesma linguagem de
        // expressão validando o template.
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(templateResolver);

        writer = new PdfExportWriter(templateEngine);
    }

    @Test
    @DisplayName("declara o formato PDF no enum do contrato")
    void declaresPdfFormat() {
        assertThat(writer.format()).isEqualTo(ExportFormat.PDF);
    }

    @Test
    @DisplayName("retorna array vazio quando a lista de especificações é vazia")
    void returnsEmptyForEmptyList() {
        byte[] result = writer.write(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("gera um arquivo PDF binário válido com cabeçalho %PDF para dados automotivos")
    void generatesValidPdfForVehicleSpecs() {
        List<VehicleSpecRow> rows = List.of(
                new VehicleSpecRow("Ford", "Ranger Raptor", "3.0 V6 Bi-Turbo", 2025,
                        "Motor e Transmissão", "Potência", "397 cv", "OFFICIAL"),
                new VehicleSpecRow("Ford", "Ranger Raptor", "3.0 V6 Bi-Turbo", 2025,
                        "Motor e Transmissão", "Torque", "59,4 kgfm", "OFFICIAL"),
                new VehicleSpecRow("Ford", "Ranger Raptor", "3.0 V6 Bi-Turbo", 2025,
                        "Motor e Transmissão", "Cilindrada", "2.956 cm³", "OFFICIAL"),
                new VehicleSpecRow("Ford", "Ranger Raptor", "3.0 V6 Bi-Turbo", 2025,
                        "Rodas", "Aro (polegadas)", "17", "OFFICIAL"),
                new VehicleSpecRow("Ford", "Ranger Raptor", "3.0 V6 Bi-Turbo", 2025,
                        "Segurança", "Airbags (quantidade)", "7", "REVIEW"),
                new VehicleSpecRow("Ford", "Ranger Raptor", "3.0 V6 Bi-Turbo", 2025,
                        "Tecnologia Avançada", "Piloto Automático Adaptativo", "Sim, com Stop & Go", "OFFICIAL")
        );

        byte[] pdfBytes = writer.write(rows);

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(1000);

        // Valida o número mágico padrão de cabeçalho do PDF (%PDF-)
        String header = new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII);
        assertThat(header).isEqualTo("%PDF-");
    }

    private VehicleSpecRow row(String categoria, String campo, String valor, String fonte) {
        return new VehicleSpecRow("Ford", "Ranger", "Raptor", 2026, categoria, campo, valor, fonte);
    }

    @Test
    @DisplayName("não quebra quando a pesquisa não preencheu nenhum dado de destaque")
    void rendersWithoutHighlights() {
        // Nenhum dos campos que viram card de destaque (motor, potência, torque,
        // câmbio, tração) está presente. Com acesso por ponto no template, o
        // SpringEL estourava aqui — chave ausente em Map não devolve null.
        byte[] pdf = writer.write(List.of(row("Rodas", "Aro (polegadas)", "17", "OFFICIAL")));

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("NOT_FOUND ganha rótulo e estilo próprios, sem se misturar a estimado")
    void notFoundHasItsOwnBadge() {
        String html = writer.renderHtml(List.of(
                row("Rodas", "Pneus Run-Flat", "Dado não encontrado", "NOT_FOUND"),
                row("Rodas", "Aro (polegadas)", "17 (estimado a partir da versão XL)", "ESTIMATED")));

        assertThat(html).contains("source-not-found").contains("Não encontrado");
        assertThat(html).contains("source-estimated").contains("Estimado");
    }

    @Test
    @DisplayName("procedência ausente nunca é apresentada como oficial")
    void neverClaimsOfficialForUnknownSource() {
        String html = writer.renderHtml(List.of(row("Rodas", "Aro (polegadas)", "17", null)));

        assertThat(html).contains("Não informado");
        assertThat(html).doesNotContain(">Oficial<");
    }

    @Test
    @DisplayName("campo sem valor cai para o traço, sem célula vazia na ficha")
    void blankValueFallsBackToDash() {
        String html = writer.renderHtml(List.of(row("Rodas", "Aro (polegadas)", "  ", "NOT_FOUND")));

        assertThat(html).contains(">-<");
    }
}

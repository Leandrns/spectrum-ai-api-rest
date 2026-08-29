package com.spectrumai.backend.export.writer;

import com.spectrumai.backend.export.ExportFormat;
import com.spectrumai.backend.export.dto.VehicleSpecRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
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

        TemplateEngine templateEngine = new TemplateEngine();
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
}

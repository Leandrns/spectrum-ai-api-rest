package com.spectrumai.backend.export.writer;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.spectrumai.backend.common.exception.BusinessException;
import com.spectrumai.backend.common.exception.ErrorCode;
import com.spectrumai.backend.export.ExportFormat;
import com.spectrumai.backend.export.dto.VehicleSpecRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gera o arquivo PDF com o layout visual e tabular de catálogo de especificações,
 * no estilo da Ficha Técnica da Ford (Ranger Raptor).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfExportWriter implements ExportWriter {

    private static final String TEMPLATE_PATH = "export/vehicle_spec_pdf";

    private final TemplateEngine templateEngine;

    @Override
    public ExportFormat format() {
        return ExportFormat.PDF;
    }

    @Override
    public byte[] write(List<VehicleSpecRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return new byte[0];
        }

        try {
            // Agrupa as linhas por veículo para suportar tanto pesquisa única quanto sessão multi-veículo
            Map<String, List<VehicleSpecRow>> rowsByVehicle = rows.stream()
                    .collect(Collectors.groupingBy(
                            this::vehicleKey,
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));

            List<VehicleExportData> vehicles = new ArrayList<>();
            for (List<VehicleSpecRow> vehicleRows : rowsByVehicle.values()) {
                VehicleSpecRow first = vehicleRows.get(0);
                Map<String, String> vehicle = Map.of(
                        "brand", nullToEmpty(first.marca()),
                        "model", nullToEmpty(first.modelo()),
                        "trim", nullToEmpty(first.versao()),
                        "year", first.anoModelo() == null ? "" : first.anoModelo().toString()
                );

                // Agrupa as especificações por categoria canônica
                Map<String, List<VehicleSpecRow>> groupedCategories = vehicleRows.stream()
                        .collect(Collectors.groupingBy(
                                VehicleSpecRow::categoria,
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

                List<CategoryExportData> categories = groupedCategories.entrySet().stream()
                        .map(entry -> new CategoryExportData(entry.getKey(), entry.getValue()))
                        .toList();

                Map<String, String> highlights = extractHighlights(vehicleRows);
                vehicles.add(new VehicleExportData(vehicle, highlights, categories));
            }

            Context context = new Context(Locale.forLanguageTag("pt-BR"));
            context.setVariable("vehicles", vehicles);
            context.setVariable("generatedAt", OffsetDateTime.now());

            String renderedHtml = templateEngine.process(TEMPLATE_PATH, context);

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(renderedHtml, "/");
            builder.toStream(os);
            builder.run();

            return os.toByteArray();
        } catch (Exception e) {
            log.error("Erro ao gerar PDF de especificações do veículo", e);
            throw new BusinessException(
                    "Falha ao gerar relatório em PDF: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.INTERNAL_ERROR
            );
        }
    }

    private Map<String, String> extractHighlights(List<VehicleSpecRow> rows) {
        Map<String, String> highlights = new LinkedHashMap<>();
        for (VehicleSpecRow row : rows) {
            String campo = row.campo();
            String valor = row.valor();
            if (valor == null || valor.isBlank() || "Dado não encontrado".equalsIgnoreCase(valor)) {
                continue;
            }

            if ("Potência".equalsIgnoreCase(campo)) {
                highlights.putIfAbsent("potencia", valor);
            } else if ("Torque".equalsIgnoreCase(campo)) {
                highlights.putIfAbsent("torque", valor);
            } else if ("Cilindrada".equalsIgnoreCase(campo) || "Motor".equalsIgnoreCase(campo)) {
                highlights.putIfAbsent("motor", valor);
            } else if ("Transmissão Automática".equalsIgnoreCase(campo) || "Quantidade de Marchas".equalsIgnoreCase(campo)) {
                highlights.putIfAbsent("transmissao", valor);
            } else if ("Tração 4x4 (High / Low)".equalsIgnoreCase(campo) || "Tração Integral (AWD)".equalsIgnoreCase(campo)) {
                highlights.putIfAbsent("tracao", valor);
            }
        }
        return highlights;
    }

    private String vehicleKey(VehicleSpecRow r) {
        return String.join("|",
                nullToEmpty(r.marca()),
                nullToEmpty(r.modelo()),
                nullToEmpty(r.versao()),
                r.anoModelo() == null ? "" : r.anoModelo().toString());
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public record VehicleExportData(
            Map<String, String> vehicle,
            Map<String, String> highlights,
            List<CategoryExportData> categories
    ) {}

    public record CategoryExportData(
            String name,
            List<VehicleSpecRow> specs
    ) {}
}

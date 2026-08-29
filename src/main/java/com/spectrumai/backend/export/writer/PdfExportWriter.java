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

    /** Valor exibido quando a pesquisa não trouxe conteúdo para o campo. */
    private static final String EMPTY_VALUE = "-";

    /**
     * Rótulo e classe CSS de cada tag de procedência.
     *
     * <p>Fica aqui, e não em ternários no template, por dois motivos: a decisão
     * era duplicada nas duas colunas do grid — bastava esquecer uma para o PDF
     * sair inconsistente — e uma tag nova precisava ser lembrada nos dois
     * lugares. Foi o que aconteceu com {@code NOT_FOUND}, que caía no ramo
     * "else" e era pintada como estimada.
     */
    private static final Map<String, SourceView> SOURCE_VIEWS = Map.of(
            "OFFICIAL", new SourceView("Oficial", "source-official"),
            "REVIEW", new SourceView("Review", "source-review"),
            "ESTIMATED", new SourceView("Estimado", "source-estimated"),
            "NOT_FOUND", new SourceView("Não encontrado", "source-not-found"));

    /**
     * Procedência ausente ou desconhecida. Nunca assumir "Oficial" aqui: o
     * relatório é usado para decisão comercial, e afirmar origem oficial de um
     * campo cuja origem se desconhece é o pior erro que este PDF pode cometer.
     */
    private static final SourceView UNKNOWN_SOURCE = new SourceView("Não informado", "source-not-found");

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
            String renderedHtml = renderHtml(rows);

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

    /**
     * Monta o modelo e renderiza o HTML da ficha, antes da conversão em PDF.
     *
     * <p>Visível para teste de propósito: os dois defeitos já corrigidos aqui
     * — {@code NOT_FOUND} pintado como estimado e a explosão do SpringEL em
     * chave ausente de {@code highlights} — viviam no template, e sobre os
     * bytes do PDF não há como afirmar nada. Sobre o HTML, há.
     */
    String renderHtml(List<VehicleSpecRow> rows) {
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
                    .map(entry -> new CategoryExportData(entry.getKey(), toViews(entry.getValue())))
                    .toList();

            Map<String, String> highlights = extractHighlights(vehicleRows);
            vehicles.add(new VehicleExportData(vehicle, highlights, categories));
        }

        Context context = new Context(Locale.forLanguageTag("pt-BR"));
        context.setVariable("vehicles", vehicles);
        context.setVariable("generatedAt", OffsetDateTime.now());

        return templateEngine.process(TEMPLATE_PATH, context);
    }

    private List<SpecRowView> toViews(List<VehicleSpecRow> rows) {
        return rows.stream().map(row -> {
            String fonte = row.fonte() == null ? "" : row.fonte().trim().toUpperCase(Locale.ROOT);
            SourceView source = SOURCE_VIEWS.getOrDefault(fonte, UNKNOWN_SOURCE);
            String valor = row.valor() == null || row.valor().isBlank() ? EMPTY_VALUE : row.valor();
            return new SpecRowView(nullToEmpty(row.campo()), valor, source.label(), source.cssClass());
        }).toList();
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
            List<SpecRowView> specs
    ) {}

    /** Linha já pronta para o template: sem lógica de decisão do lado do HTML. */
    public record SpecRowView(
            String campo,
            String valor,
            String fonteLabel,
            String fonteClass
    ) {}

    private record SourceView(String label, String cssClass) {}
}

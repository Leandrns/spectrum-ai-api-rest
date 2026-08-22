package com.spectrumai.backend.export.spec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrumai.backend.export.dto.VehicleSpecRow;
import com.spectrumai.backend.search.model.Search;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Achata o JSON de specs de uma pesquisa em linhas (veículo × categoria × campo).
 *
 * <p>A coluna {@code specs} guarda as categorias canônicas <em>na raiz</em> — o
 * {@code SearchProcessor} desembrulha o nó {@code "specs"} devolvido pelo Gemini antes
 * de persistir — e acrescenta uma chave {@code "sources"} no fim, que não é categoria
 * e sim a lista de fontes consultadas pela IA. Ela é ignorada aqui, do mesmo modo que
 * {@code SearchProcessor.countLeafFields} a ignora ao contar campos preenchidos.
 *
 * <p>O modelo nem sempre respeita o schema {@code {value, source}} à risca. Um campo
 * malformado nunca pode derrubar a exportação inteira, então o achatamento degrada
 * para o melhor valor disponível em vez de lançar exceção.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpecsFlattener {

    /** Chave de primeiro nível que não representa uma categoria de ficha técnica. */
    private static final String SOURCES_KEY = "sources";

    private final ObjectMapper objectMapper;

    /** Retorna lista vazia para pesquisas sem specs (ex.: {@code FAILED}). */
    public List<VehicleSpecRow> flatten(Search search) {
        String specs = search.getSpecs();
        if (specs == null || specs.isBlank()) {
            return List.of();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(specs);
        } catch (Exception e) {
            log.error("Specs da pesquisa {} não são JSON válido — exportando vazio", search.getId(), e);
            return List.of();
        }
        if (!root.isObject()) {
            log.warn("Specs da pesquisa {} não são um objeto JSON — exportando vazio", search.getId());
            return List.of();
        }

        String marca = nullToEmpty(search.getBrand());
        String modelo = nullToEmpty(search.getModel());
        String versao = nullToEmpty(search.getTrim());
        Integer ano = search.getYear() == null ? null : search.getYear().intValue();

        List<VehicleSpecRow> rows = new ArrayList<>();
        // O iterador de ObjectNode preserva a ordem de inserção, que é a ordem
        // canônica das categorias no prompt — o CSV sai na mesma ordem da ficha.
        for (Map.Entry<String, JsonNode> category : root.properties()) {
            if (SOURCES_KEY.equals(category.getKey())) {
                continue;
            }
            JsonNode fields = category.getValue();
            if (fields == null || !fields.isObject()) {
                continue;
            }
            for (Map.Entry<String, JsonNode> field : fields.properties()) {
                rows.add(new VehicleSpecRow(
                        marca,
                        modelo,
                        versao,
                        ano,
                        category.getKey(),
                        field.getKey(),
                        readValue(field.getValue()),
                        readSource(field.getValue())
                ));
            }
        }
        return rows;
    }

    /** Aceita tanto {@code {"value": ...}} quanto um valor cru fora do schema. */
    private String readValue(JsonNode field) {
        if (field == null || field.isNull()) {
            return "";
        }
        if (field.isObject()) {
            return asText(field.path("value"));
        }
        return asText(field);
    }

    /** Fonte vazia quando o campo veio fora do schema — não há como inferi-la. */
    private String readSource(JsonNode field) {
        if (field == null || !field.isObject()) {
            return "";
        }
        return asText(field.path("source"));
    }

    private String asText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        // Objetos e arrays viram JSON compacto: melhor um valor bruto na célula
        // do que perder o dado silenciosamente.
        return node.isValueNode() ? node.asText() : node.toString();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}

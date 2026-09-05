package com.spectrumai.backend.export.bigquery;

import com.spectrumai.backend.export.bigquery.dto.VehicleSpecFact;
import com.spectrumai.backend.export.dto.VehicleSpecRow;
import com.spectrumai.backend.search.model.Search;
import com.spectrumai.backend.search.model.SourceType;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converte as linhas achatadas de uma pesquisa nos fatos gravados no BigQuery. */
@Component
public class VehicleSpecFactMapper {

    /**
     * Número no início do valor, com grupos decimais ou de milhar em qualquer
     * combinação: {@code 177}, {@code 21,0}, {@code 1.999}, {@code 1.499,90}.
     */
    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\s*(-?\\d+(?:[.,]\\d+)*)");

    /**
     * Número cujos pontos são todos separadores de milhar: cada grupo tem exatamente
     * três dígitos. É o que distingue {@code 1.999} (mil novecentos e noventa e nove)
     * de {@code 1.6} (a designação do motor).
     */
    private static final Pattern THOUSANDS_GROUPED = Pattern.compile("-?\\d{1,3}(?:\\.\\d{3})+");

    public List<VehicleSpecFact> toFacts(Search search, List<VehicleSpecRow> rows,
                                         OffsetDateTime ingestedAt) {
        // A pesquisa concluída sempre tem completed_at; o fallback existe porque a
        // coluna é a chave de particionamento e um nulo mandaria a linha para a
        // partição __NULL__, fora do alcance de qualquer filtro por data.
        OffsetDateTime pesquisadoEm = search.getCompletedAt() == null
                ? ingestedAt
                : search.getCompletedAt();

        List<VehicleSpecFact> facts = new ArrayList<>(rows.size());
        for (VehicleSpecRow row : rows) {
            boolean encontrado = !SourceType.NOT_FOUND.name().equals(row.fonte());
            facts.add(new VehicleSpecFact(
                    search.getTenantId(),
                    search.getId(),
                    search.getSession() == null ? null : search.getSession().getId(),
                    row.marca(),
                    row.modelo(),
                    row.versao(),
                    row.anoModelo(),
                    row.categoria(),
                    row.campo(),
                    row.valor(),
                    row.fonte(),
                    encontrado,
                    encontrado ? parseLeadingNumber(row.valor()) : null,
                    search.getConfidence(),
                    pesquisadoEm,
                    ingestedAt));
        }
        return facts;
    }

    /**
     * Extrai o primeiro número do valor, para que o BI possa agregar sem precisar de
     * uma expressão calculada em cada dashboard: {@code "177 cv"} vira {@code 177.0}
     * e {@code "21,0 kgfm"} vira {@code 21.0}.
     *
     * <p>É deliberadamente best-effort, e a coluna {@code valor} continua sendo a
     * referência. Duas ambiguidades justificam a ressalva:
     *
     * <ul>
     *   <li>A ficha mistura convenções — {@code "1.999 cm³"} é mil novecentos e
     *       noventa e nove, mas {@code "1.6"} é a designação do motor. A regra
     *       adotada é a do português: ponto seguido de exatamente três dígitos é
     *       separador de milhar, qualquer outro ponto é decimal.</li>
     *   <li>Valores com mais de um número ({@code "17/18"}, {@code "2 airbags"})
     *       devolvem o primeiro, que nem sempre é o relevante.</li>
     * </ul>
     *
     * @return o número, ou {@code null} quando o valor não começa por um
     */
    static Double parseLeadingNumber(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        Matcher matcher = LEADING_NUMBER.matcher(valor);
        if (!matcher.find()) {
            return null;
        }
        String token = matcher.group(1);

        String normalized;
        if (token.indexOf(',') >= 0) {
            // Com vírgula presente, ela é o separador decimal e o ponto é de milhar.
            normalized = token.replace(".", "").replace(',', '.');
        } else if (THOUSANDS_GROUPED.matcher(token).matches()) {
            normalized = token.replace(".", "");
        } else {
            normalized = token;
        }

        try {
            return Double.valueOf(normalized);
        } catch (NumberFormatException e) {
            // Ambiguidade que as regras acima não resolvem, como "1.20.3".
            return null;
        }
    }
}

package com.spectrumai.backend.export.bigquery;

import com.google.cloud.bigquery.Clustering;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TimePartitioning;
import com.spectrumai.backend.export.bigquery.dto.VehicleSpecFact;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Definição da tabela de fatos no BigQuery: colunas, particionamento, clustering
 * e a view de leitura. Tudo num só lugar porque os nomes de coluna aparecem em
 * três contextos que precisam concordar — a criação da tabela, a montagem da linha
 * enviada no {@code insertAll} e o SQL da view.
 *
 * <p>O layout é o mesmo long/tidy do CSV (uma linha por campo da ficha), pelo mesmo
 * motivo descrito em {@link com.spectrumai.backend.export.dto.VehicleSpecRow}: em
 * formato wide, as colunas mudariam a cada revisão do prompt e quebrariam dashboards
 * já montados. O que a tabela acrescenta ao arquivo são as chaves analíticas
 * ({@code search_id}, {@code session_id}, {@code tenant_id}) e os campos derivados
 * que o BI usaria numa expressão calculada de qualquer forma.
 */
public final class BigQuerySchema {

    public static final String TENANT_ID = "tenant_id";
    public static final String SEARCH_ID = "search_id";
    public static final String SESSION_ID = "session_id";
    public static final String MARCA = "marca";
    public static final String MODELO = "modelo";
    public static final String VERSAO = "versao";
    public static final String ANO_MODELO = "ano_modelo";
    public static final String CATEGORIA = "categoria";
    public static final String CAMPO = "campo";
    public static final String VALOR = "valor";
    public static final String FONTE = "fonte";
    public static final String ENCONTRADO = "encontrado";
    public static final String VALOR_NUM = "valor_num";
    public static final String CONFIANCA = "confianca";
    public static final String PESQUISADO_EM = "pesquisado_em";
    public static final String INGERIDO_EM = "ingerido_em";

    private BigQuerySchema() {}

    public static Schema schema() {
        return Schema.of(
                required(TENANT_ID, StandardSQLTypeName.STRING,
                        "Empresa dona da pesquisa. Toda consulta deve filtrar por aqui."),
                required(SEARCH_ID, StandardSQLTypeName.STRING,
                        "Pesquisa que originou a linha. Permite voltar à API pelo histórico."),
                nullable(SESSION_ID, StandardSQLTypeName.STRING,
                        "Sessão de análise, quando a pesquisa nasceu dentro de um comparativo."),
                nullable(MARCA, StandardSQLTypeName.STRING, "Marca do veículo."),
                nullable(MODELO, StandardSQLTypeName.STRING, "Modelo do veículo."),
                nullable(VERSAO, StandardSQLTypeName.STRING, "Versão/trim pesquisada."),
                nullable(ANO_MODELO, StandardSQLTypeName.INT64, "Ano-modelo pesquisado."),
                nullable(CATEGORIA, StandardSQLTypeName.STRING,
                        "Categoria canônica da ficha técnica (Motor e Transmissão, Rodas, ...)."),
                nullable(CAMPO, StandardSQLTypeName.STRING, "Campo dentro da categoria."),
                nullable(VALOR, StandardSQLTypeName.STRING,
                        "Valor como a IA o retornou. É o dado de referência — os campos "
                                + "derivados abaixo são conveniência."),
                nullable(FONTE, StandardSQLTypeName.STRING,
                        "Procedência do valor: OFFICIAL, REVIEW, ESTIMATED ou NOT_FOUND."),
                nullable(ENCONTRADO, StandardSQLTypeName.BOOL,
                        "false quando fonte = NOT_FOUND. Mede cobertura sem comparar strings."),
                nullable(VALOR_NUM, StandardSQLTypeName.FLOAT64,
                        "Primeiro número de `valor`, quando existe. Best-effort: confira em "
                                + "`valor` antes de usar num indicador de negócio."),
                nullable(CONFIANCA, StandardSQLTypeName.NUMERIC,
                        "Confiança geral da pesquisa (0 a 1), igual para todas as linhas dela."),
                required(PESQUISADO_EM, StandardSQLTypeName.TIMESTAMP,
                        "Conclusão da pesquisa. Coluna de particionamento."),
                required(INGERIDO_EM, StandardSQLTypeName.TIMESTAMP,
                        "Carga que trouxe a linha. Desempata revisões da mesma pesquisa."));
    }

    /**
     * Particionamento diário por {@code pesquisado_em} — e não pela data de carga,
     * porque toda pergunta do negócio é sobre quando o veículo foi pesquisado, e é
     * assim que o filtro elimina partições.
     */
    public static TimePartitioning timePartitioning(int retentionDays) {
        TimePartitioning.Builder builder = TimePartitioning
                .newBuilder(TimePartitioning.Type.DAY)
                .setField(PESQUISADO_EM);
        if (retentionDays > 0) {
            builder.setExpirationMs(Duration.ofDays(retentionDays).toMillis());
        }
        return builder.build();
    }

    /**
     * Clustering com o tenant à frente: é o filtro presente em toda consulta, então
     * é ele que corta mais bytes lidos. Marca e modelo vêm depois porque são o
     * recorte natural de um comparativo.
     */
    public static Clustering clustering() {
        return Clustering.newBuilder()
                .setFields(List.of(TENANT_ID, MARCA, MODELO, CATEGORIA))
                .build();
    }

    public static String latestViewName(String table) {
        return "vw_" + table + "_latest";
    }

    /**
     * View que resolve a tabela append-only para "uma linha por campo": a carga mais
     * recente de cada {@code (pesquisa, categoria, campo)} vence.
     *
     * <p>A tabela é append-only porque linhas recém-inseridas pela streaming API
     * ficam num buffer que não aceita DML por até 90 minutos — um {@code DELETE}
     * antes do insert falharia de forma intermitente. Empilhar e resolver na leitura
     * troca esse problema por uma view, e de graça mantém o histórico de revisões.
     *
     * <p>É esta view, e não a tabela, que o BI deve consultar.
     */
    public static String latestViewQuery(String projectId, String dataset, String table) {
        return """
                SELECT * EXCEPT(_revisao)
                FROM (
                  SELECT
                    *,
                    ROW_NUMBER() OVER (
                      PARTITION BY %1$s, %2$s, %3$s, %4$s
                      ORDER BY %5$s DESC
                    ) AS _revisao
                  FROM `%6$s.%7$s.%8$s`
                )
                WHERE _revisao = 1
                """.formatted(TENANT_ID, SEARCH_ID, CATEGORIA, CAMPO, INGERIDO_EM,
                projectId, dataset, table);
    }

    /**
     * Converte o fato na linha aceita pelo {@code insertAll}.
     *
     * <p>Chaves nulas são omitidas em vez de enviadas como {@code null}: o payload
     * de uma ficha técnica é esparso e a API trata ausência e nulo do mesmo jeito.
     *
     * <p>{@code TIMESTAMP} vai como ISO-8601 em UTC e {@code NUMERIC} como texto —
     * a API aceita ambos e nenhum dos dois passa por {@code double}, onde a
     * confiança perderia precisão.
     */
    public static Map<String, Object> toRow(VehicleSpecFact fact) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(TENANT_ID, fact.tenantId().toString());
        row.put(SEARCH_ID, fact.searchId().toString());
        putIfPresent(row, SESSION_ID, fact.sessionId() == null ? null : fact.sessionId().toString());
        putIfPresent(row, MARCA, blankToNull(fact.marca()));
        putIfPresent(row, MODELO, blankToNull(fact.modelo()));
        putIfPresent(row, VERSAO, blankToNull(fact.versao()));
        putIfPresent(row, ANO_MODELO, fact.anoModelo());
        putIfPresent(row, CATEGORIA, blankToNull(fact.categoria()));
        putIfPresent(row, CAMPO, blankToNull(fact.campo()));
        putIfPresent(row, VALOR, blankToNull(fact.valor()));
        putIfPresent(row, FONTE, blankToNull(fact.fonte()));
        row.put(ENCONTRADO, fact.encontrado());
        putIfPresent(row, VALOR_NUM, fact.valorNum());
        putIfPresent(row, CONFIANCA, fact.confianca() == null ? null : fact.confianca().toPlainString());
        row.put(PESQUISADO_EM, timestamp(fact.pesquisadoEm()));
        row.put(INGERIDO_EM, timestamp(fact.ingeridoEm()));
        return row;
    }

    private static String timestamp(OffsetDateTime value) {
        return DateTimeFormatter.ISO_INSTANT.format(value.toInstant());
    }

    private static void putIfPresent(Map<String, Object> row, String column, Object value) {
        if (value != null) {
            row.put(column, value);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Field required(String name, StandardSQLTypeName type, String description) {
        return field(name, type, description, Field.Mode.REQUIRED);
    }

    private static Field nullable(String name, StandardSQLTypeName type, String description) {
        return field(name, type, description, Field.Mode.NULLABLE);
    }

    private static Field field(String name, StandardSQLTypeName type, String description, Field.Mode mode) {
        return Field.newBuilder(name, type)
                .setMode(mode)
                .setDescription(description)
                .build();
    }
}

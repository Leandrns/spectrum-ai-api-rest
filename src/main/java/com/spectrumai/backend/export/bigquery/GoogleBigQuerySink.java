package com.spectrumai.backend.export.bigquery;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryError;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.InsertAllResponse;
import com.google.cloud.bigquery.StandardTableDefinition;
import com.google.cloud.bigquery.Table;
import com.google.cloud.bigquery.TableDefinition;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableInfo;
import com.google.cloud.bigquery.ViewDefinition;
import com.spectrumai.backend.common.exception.BusinessException;
import com.spectrumai.backend.common.exception.ErrorCode;
import com.spectrumai.backend.common.gcp.GcpCredentials;
import com.spectrumai.backend.common.util.ContentHash;
import com.spectrumai.backend.config.AppProperties;
import com.spectrumai.backend.export.bigquery.dto.VehicleSpecFact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ingestão no BigQuery pela streaming API ({@code insertAll}).
 *
 * <p>A escolha da streaming API em vez de um load job a partir do bucket é de escala:
 * uma pesquisa produz algumas centenas de linhas, o insert fica consultável na hora e
 * não há arquivo intermediário para limpar. Se o volume um dia crescer a ponto de a
 * cota de streaming pesar, o caminho é gravar NDJSON no bucket que já existe e trocar
 * esta implementação por uma que dispare um load job — o resto do módulo não muda.
 *
 * <p>Credenciais e projeto caem na configuração do Cloud Storage quando não têm valor
 * próprio: é a mesma service account, no mesmo projeto, e duplicar isso no ambiente
 * só criaria uma chance de os dois divergirem.
 *
 * <p>O client é construído na primeira utilização, e não no boot, pelo mesmo motivo do
 * {@link com.spectrumai.backend.export.storage.GcsExportStorage}: uma instalação de
 * desenvolvimento sem credencial GCP precisa conseguir subir a aplicação.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleBigQuerySink implements BigQuerySink {

    /** Tamanho do {@code insertId}, em caracteres hex. O limite da API é 128. */
    private static final int INSERT_ID_LENGTH = 32;

    private final AppProperties properties;

    private volatile BigQuery client;
    private volatile boolean schemaReady;

    @Override
    public void ensureSchema() {
        if (schemaReady) {
            return;
        }
        synchronized (this) {
            if (schemaReady) {
                return;
            }
            ensureTable();
            ensureLatestView();
            schemaReady = true;
        }
    }

    @Override
    public void insertAll(List<VehicleSpecFact> facts) {
        if (facts.isEmpty()) {
            return;
        }
        InsertAllRequest.Builder request = InsertAllRequest.newBuilder(tableId());
        for (VehicleSpecFact fact : facts) {
            request.addRow(insertId(fact), BigQuerySchema.toRow(fact));
        }

        InsertAllResponse response;
        try {
            response = client().insertAll(request.build());
        } catch (BigQueryException e) {
            throw warehouseFailure("inserir as linhas", e);
        }

        if (response.hasErrors()) {
            // insertAll é parcial: linhas boas entram, ruins não. Falhar aqui é o que
            // impede o registro de controle de gravar um hash que a tabela não tem.
            logInsertErrors(response);
            throw new BusinessException(
                    "O BigQuery rejeitou " + response.getInsertErrors().size()
                            + " das " + facts.size() + " linhas enviadas.",
                    HttpStatus.BAD_GATEWAY,
                    ErrorCode.WAREHOUSE_ERROR);
        }
    }

    /**
     * Cria a tabela de fatos particionada e clusterizada, se ela ainda não existir.
     *
     * <p>Criar pela aplicação, e não à mão, é o que garante o particionamento: uma
     * tabela criada sem ele funciona igual e só se revela na fatura, porque toda
     * consulta passa a varrer o histórico inteiro.
     */
    private void ensureTable() {
        TableId tableId = tableId();
        if (getTable(tableId) != null) {
            return;
        }
        StandardTableDefinition definition = StandardTableDefinition.newBuilder()
                .setSchema(BigQuerySchema.schema())
                .setTimePartitioning(BigQuerySchema.timePartitioning(bigQuery().retentionDays()))
                .setClustering(BigQuerySchema.clustering())
                .build();
        try {
            client().create(TableInfo.newBuilder(tableId, definition)
                    .setDescription("Spectrum AI — fichas técnicas pesquisadas, uma linha por campo. "
                            + "Consulte a view " + BigQuerySchema.latestViewName(table())
                            + ", que resolve revisões da mesma pesquisa.")
                    .build());
            log.info("Tabela criada no BigQuery: {}.{}.{}", projectId(), dataset(), table());
        } catch (BigQueryException e) {
            // Corrida entre duas instâncias subindo juntas: a segunda encontra a
            // tabela já criada, e isso é sucesso, não erro.
            if (getTable(tableId) != null) {
                log.debug("Tabela do BigQuery já criada por outra instância");
                return;
            }
            // Criar tabela não pode dar 404 pela tabela — só pelo dataset que a conteria.
            if (e.getCode() == 404) {
                throw datasetMissing(e);
            }
            throw warehouseFailure("criar a tabela", e);
        }
    }

    private BusinessException datasetMissing(BigQueryException cause) {
        log.error("Dataset {}.{} não encontrado no BigQuery", projectId(), dataset(), cause);
        return new BusinessException(
                "O dataset " + dataset() + " não existe no projeto " + projectId()
                        + ". Ele precisa ser criado antes, na mesma localização do bucket.",
                HttpStatus.BAD_GATEWAY,
                ErrorCode.WAREHOUSE_ERROR);
    }

    private void ensureLatestView() {
        TableId viewId = TableId.of(projectId(), dataset(), BigQuerySchema.latestViewName(table()));
        String query = BigQuerySchema.latestViewQuery(projectId(), dataset(), table());
        Table existing = getTable(viewId);
        try {
            if (existing == null) {
                client().create(TableInfo.newBuilder(viewId, ViewDefinition.of(query))
                        .setDescription("Uma linha por (pesquisa, categoria, campo): a carga mais "
                                + "recente vence. É esta a view que o BI deve consultar.")
                        .build());
                log.info("View criada no BigQuery: {}", viewId.getTable());
            } else if (queryChanged(existing.getDefinition(), query)) {
                // A definição da view fica congelada no BigQuery; sem isso, uma coluna
                // nova na tabela nunca apareceria para quem consulta.
                client().update(existing.toBuilder().setDefinition(ViewDefinition.of(query)).build());
                log.info("View atualizada no BigQuery: {}", viewId.getTable());
            }
        } catch (BigQueryException e) {
            throw warehouseFailure("criar a view de leitura", e);
        }
    }

    private boolean queryChanged(TableDefinition definition, String query) {
        return definition instanceof ViewDefinition view && !query.equals(view.getQuery());
    }

    /**
     * Devolve {@code null} quando a tabela — ou o dataset que a conteria — não existe.
     * O client do BigQuery traduz 404 em {@code null} e não distingue os dois casos;
     * quem separa é {@link #ensureTable()}, na falha da criação.
     */
    private Table getTable(TableId tableId) {
        try {
            return client().getTable(tableId);
        } catch (BigQueryException e) {
            throw warehouseFailure("consultar a tabela", e);
        }
    }

    /**
     * Chave de deduplicação best-effort da própria API, derivada do conteúdo da linha.
     * Cobre a janela em que uma tentativa repetida (timeout que na verdade gravou)
     * duplicaria linhas — o registro de controle em {@code bigquery_syncs} só protege
     * chamadas que chegaram a terminar.
     */
    private String insertId(VehicleSpecFact fact) {
        return ContentHash.of(fact.fingerprint()).substring(0, INSERT_ID_LENGTH);
    }

    private void logInsertErrors(InsertAllResponse response) {
        response.getInsertErrors().entrySet().stream().limit(5).forEach(entry -> {
            String messages = entry.getValue().stream()
                    .map(BigQueryError::getMessage)
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("sem detalhe");
            log.error("BigQuery rejeitou a linha {}: {}", entry.getKey(), messages);
        });
        if (response.getInsertErrors().size() > 5) {
            log.error("... e outras {} linhas rejeitadas", response.getInsertErrors().size() - 5);
        }
    }

    private BigQuery client() {
        BigQuery local = this.client;
        if (local == null) {
            synchronized (this) {
                local = this.client;
                if (local == null) {
                    local = this.client = buildClient();
                }
            }
        }
        return local;
    }

    private BigQuery buildClient() {
        BigQueryOptions.Builder builder = BigQueryOptions.newBuilder();
        String projectId = projectId();
        if (projectId != null && !projectId.isBlank()) {
            builder.setProjectId(projectId);
        }
        try {
            ServiceAccountCredentials inline = GcpCredentials.fromJsonProperty(
                    credentialsJson(), "BQ_CREDENTIALS_JSON/GCP_CREDENTIALS_JSON");
            if (inline != null) {
                builder.setCredentials(inline);
                log.info("BigQuery autenticado pela credencial inline (service account {})",
                        inline.getClientEmail());
            }
            // Sem credencial inline, cai no Application Default Credentials.
            return builder.build().getService();
        } catch (RuntimeException e) {
            log.error("Falha ao inicializar o client do BigQuery", e);
            throw new BusinessException(
                    "Integração com o BigQuery indisponível.",
                    HttpStatus.BAD_GATEWAY,
                    ErrorCode.WAREHOUSE_ERROR);
        }
    }

    private TableId tableId() {
        return TableId.of(projectId(), dataset(), table());
    }

    private String projectId() {
        String own = bigQuery().projectId();
        if (own != null && !own.isBlank()) {
            return own;
        }
        AppProperties.Storage.Gcs gcs = gcsOrNull();
        return gcs == null ? null : gcs.projectId();
    }

    private String credentialsJson() {
        String own = bigQuery().credentialsJson();
        if (own != null && !own.isBlank()) {
            return own;
        }
        AppProperties.Storage.Gcs gcs = gcsOrNull();
        return gcs == null ? null : gcs.credentialsJson();
    }

    private String dataset() {
        return required(bigQuery().dataset(), "spectrum.bigquery.dataset", "BQ_DATASET");
    }

    private String table() {
        return required(bigQuery().table(), "spectrum.bigquery.table", "BQ_TABLE");
    }

    private String required(String value, String property, String envVar) {
        if (value == null || value.isBlank()) {
            log.error("{} não configurado — defina {}", property, envVar);
            throw new BusinessException(
                    "Integração com o BigQuery não configurada.",
                    HttpStatus.BAD_GATEWAY,
                    ErrorCode.WAREHOUSE_ERROR);
        }
        return value;
    }

    private AppProperties.BigQuery bigQuery() {
        if (properties.bigquery() == null) {
            throw new BusinessException(
                    "Integração com o BigQuery não configurada.",
                    HttpStatus.BAD_GATEWAY,
                    ErrorCode.WAREHOUSE_ERROR);
        }
        return properties.bigquery();
    }

    private AppProperties.Storage.Gcs gcsOrNull() {
        return properties.storage() == null ? null : properties.storage().gcs();
    }

    /** Mensagem genérica ao cliente; o detalhe da API do Google fica só no log. */
    private BusinessException warehouseFailure(String acao, Exception cause) {
        log.error("Falha ao {} no BigQuery: {}.{}.{}", acao, projectId(), dataset(), table(), cause);
        return new BusinessException(
                "Não foi possível " + acao + " no BigQuery. Tente novamente em instantes.",
                HttpStatus.BAD_GATEWAY,
                ErrorCode.WAREHOUSE_ERROR);
    }
}

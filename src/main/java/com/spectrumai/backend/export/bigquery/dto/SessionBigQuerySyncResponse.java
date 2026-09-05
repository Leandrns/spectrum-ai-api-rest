package com.spectrumai.backend.export.bigquery.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resposta de {@code POST /v1/sessions/&#123;id&#125;/export/bigquery}.
 *
 * <p>Diferente da exportação em arquivo, a sessão não deduplica veículo repetido: no
 * BigQuery cada linha carrega seu {@code search_id}, então as duas pesquisas do mesmo
 * veículo convivem e quem escolhe é a consulta.
 *
 * @param sessionId  a sessão sincronizada
 * @param searches   pesquisas concluídas consideradas
 * @param synced     pesquisas que tiveram linhas enviadas agora
 * @param skipped    pesquisas inalteradas desde a última carga, ou sem especificações
 * @param rows       total de linhas enviadas nesta chamada
 * @param ingestedAt carimbo desta carga
 */
public record SessionBigQuerySyncResponse(
        UUID sessionId,
        int searches,
        int synced,
        int skipped,
        int rows,
        OffsetDateTime ingestedAt
) {}

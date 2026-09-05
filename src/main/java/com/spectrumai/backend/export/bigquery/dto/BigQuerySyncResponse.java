package com.spectrumai.backend.export.bigquery.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resposta de {@code POST /v1/searches/&#123;id&#125;/export/bigquery}.
 *
 * @param searchId   a pesquisa sincronizada
 * @param rows       linhas que a pesquisa representa na tabela
 * @param skipped    {@code true} quando o conteúdo não mudou desde a última carga e
 *                   nada foi reenviado — a consulta no BI já reflete estes dados
 * @param ingestedAt carimbo da carga vigente; em {@code skipped}, o da carga anterior
 */
public record BigQuerySyncResponse(
        UUID searchId,
        int rows,
        boolean skipped,
        OffsetDateTime ingestedAt
) {}

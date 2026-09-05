package com.spectrumai.backend.export.bigquery.service;

import com.spectrumai.backend.export.bigquery.dto.BigQuerySyncResponse;
import com.spectrumai.backend.export.bigquery.dto.SessionBigQuerySyncResponse;
import com.spectrumai.backend.search.model.Search;
import com.spectrumai.backend.session.model.AnalysisSession;

import java.util.UUID;

/**
 * Leva as fichas técnicas pesquisadas para a tabela de fatos do BigQuery.
 *
 * <p>Recebe entidades já resolvidas nos métodos de uso interativo, pelo mesmo motivo
 * de {@link com.spectrumai.backend.export.service.ExportService}: a validação de
 * tenant fica com quem carrega o recurso, numa única fronteira por recurso.
 *
 * <p>As operações são idempotentes — reenviar uma pesquisa cujo conteúdo não mudou
 * não grava nada.
 */
public interface BigQueryExportService {

    BigQuerySyncResponse syncSearch(Search search);

    SessionBigQuerySyncResponse syncSession(AnalysisSession session);

    /**
     * Sincroniza uma pesquisa recém-concluída, sem nunca propagar erro.
     *
     * <p>É o gancho automático do fim da pesquisa: uma indisponibilidade do BigQuery
     * não pode marcar como falha uma pesquisa que a IA respondeu com sucesso. Quando
     * a carga não acontece, a pesquisa fica ausente da tabela até alguém chamar o
     * endpoint — o que é aceitável enquanto não existir o job de reconciliação.
     */
    void syncCompletedSearchQuietly(UUID searchId);
}

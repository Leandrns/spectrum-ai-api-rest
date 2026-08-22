package com.spectrumai.backend.export.service;

import com.spectrumai.backend.export.ExportFormat;
import com.spectrumai.backend.search.dto.SearchExportResponse;
import com.spectrumai.backend.search.model.Search;
import com.spectrumai.backend.session.model.AnalysisSession;

/**
 * Gera, armazena e disponibiliza os arquivos de exportação.
 *
 * <p>Recebe entidades já resolvidas em vez de identificadores: a validação de tenant
 * continua sendo responsabilidade de quem carrega o recurso
 * ({@code SearchServiceImpl.loadSearchScoped} e {@code SessionService.getById}),
 * mantendo uma única fronteira de isolamento por recurso.
 */
public interface ExportService {

    SearchExportResponse exportSearch(Search search, ExportFormat format);

    SearchExportResponse exportSession(AnalysisSession session, ExportFormat format);
}

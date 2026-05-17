package com.spectrumai.backend.search.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento de progresso publicado via SSE durante o ciclo de vida de uma pesquisa.
 * Schema estável: mesmos campos servem para eventos reais (futuro scraping multi-fonte)
 * e sintéticos (loading messages do FakeProgressScheduler).
 */
public record SearchProgressEvent(
        UUID searchId,
        String phase,
        String source,
        String sourceStatus,
        Integer fieldsExtracted,
        Integer progressPercent,
        String message,
        OffsetDateTime timestamp,
        boolean synthetic
) {

    public static final String PHASE_QUEUED = "QUEUED";
    public static final String PHASE_PROCESSING = "PROCESSING";
    public static final String PHASE_SOURCE_PROGRESS = "SOURCE_PROGRESS";
    public static final String PHASE_COMPLETED = "COMPLETED";
    public static final String PHASE_FAILED = "FAILED";

    public static final String SOURCE_STATUS_CONSULTING = "consultando";
    public static final String SOURCE_STATUS_DONE = "concluida";
    public static final String SOURCE_STATUS_FAILED = "falhou";

    public static SearchProgressEvent queued(UUID searchId) {
        return new SearchProgressEvent(searchId, PHASE_QUEUED, null, null, 0, 0,
                "Pesquisa enfileirada", OffsetDateTime.now(), false);
    }

    public static SearchProgressEvent processing(UUID searchId) {
        return new SearchProgressEvent(searchId, PHASE_PROCESSING, null, null, 0, 2,
                "Iniciando coleta de especificações", OffsetDateTime.now(), false);
    }

    public static SearchProgressEvent completed(UUID searchId, int fieldsExtracted) {
        return new SearchProgressEvent(searchId, PHASE_COMPLETED, null, null, fieldsExtracted, 100,
                "Pesquisa concluída", OffsetDateTime.now(), false);
    }

    public static SearchProgressEvent failed(UUID searchId, String reason) {
        return new SearchProgressEvent(searchId, PHASE_FAILED, null, SOURCE_STATUS_FAILED, null, null,
                reason == null ? "Falha na pesquisa" : reason, OffsetDateTime.now(), false);
    }

    public static SearchProgressEvent syntheticSource(UUID searchId, String source, String sourceStatus,
                                                       int fieldsExtracted, int progressPercent, String message) {
        return new SearchProgressEvent(searchId, PHASE_SOURCE_PROGRESS, source, sourceStatus,
                fieldsExtracted, progressPercent, message, OffsetDateTime.now(), true);
    }

    public boolean isTerminal() {
        return PHASE_COMPLETED.equals(phase) || PHASE_FAILED.equals(phase);
    }
}

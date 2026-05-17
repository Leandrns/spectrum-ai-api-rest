package com.spectrumai.backend.search.service;

import com.spectrumai.backend.search.dto.SearchProgressEvent;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Emite eventos sintéticos de progresso ("fake loading") enquanto o Gemini processa
 * uma pesquisa de forma síncrona. Roteiro fixo simulando consulta às fontes que o
 * RF06 prevê (Webmotors, iCarros, Quatro Rodas, YouTube etc.).
 *
 * Quando o evento real terminal (COMPLETED/FAILED) chegar, o roteiro restante é
 * cancelado via {@link #cancel(UUID)}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FakeProgressScheduler {

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "fake-progress-scheduler");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<UUID, List<ScheduledFuture<?>>> scheduledBySearch = new ConcurrentHashMap<>();

    @Lazy
    private final SearchStreamService streamService;

    private record Step(long delaySeconds, String source, String sourceStatus,
                        int fieldsExtracted, int progressPercent, String message) {}

    private static final List<Step> SCRIPT = List.of(
            new Step(0,  "gemini",       SearchProgressEvent.SOURCE_STATUS_CONSULTING, 0,  5,  "Iniciando análise…"),
            new Step(2,  "ford-oficial", SearchProgressEvent.SOURCE_STATUS_CONSULTING, 0,  12, "Consultando site oficial Ford…"),
            new Step(6,  "ford-oficial", SearchProgressEvent.SOURCE_STATUS_DONE,       8,  22, "Site oficial consultado (8 campos)"),
            new Step(7,  "webmotors",    SearchProgressEvent.SOURCE_STATUS_CONSULTING, 8,  25, "Consultando Webmotors…"),
            new Step(12, "webmotors",    SearchProgressEvent.SOURCE_STATUS_DONE,       14, 38, "Webmotors consultado (+6 campos)"),
            new Step(13, "icarros",      SearchProgressEvent.SOURCE_STATUS_CONSULTING, 14, 42, "Consultando iCarros…"),
            new Step(18, "icarros",      SearchProgressEvent.SOURCE_STATUS_DONE,       19, 55, "iCarros consultado (+5 campos)"),
            new Step(19, "quatro-rodas", SearchProgressEvent.SOURCE_STATUS_CONSULTING, 19, 58, "Consultando Quatro Rodas…"),
            new Step(23, "quatro-rodas", SearchProgressEvent.SOURCE_STATUS_DONE,       22, 70, "Quatro Rodas consultado (+3 campos)"),
            new Step(24, "youtube",      SearchProgressEvent.SOURCE_STATUS_CONSULTING, 22, 73, "Buscando reviews no YouTube…"),
            new Step(30, "youtube",      SearchProgressEvent.SOURCE_STATUS_DONE,       25, 85, "3 reviews analisados (+3 campos)"),
            new Step(31, "gemini",       SearchProgressEvent.SOURCE_STATUS_CONSULTING, 25, 90, "Consolidando dados com IA…")
    );

    public void start(UUID searchId) {
        if (scheduledBySearch.containsKey(searchId)) {
            log.debug("Fake scheduler já ativo para {}", searchId);
            return;
        }

        List<ScheduledFuture<?>> futures = new ArrayList<>(SCRIPT.size());
        for (Step step : SCRIPT) {
            ScheduledFuture<?> future = executor.schedule(
                    () -> emit(searchId, step),
                    step.delaySeconds(),
                    TimeUnit.SECONDS);
            futures.add(future);
        }

        scheduledBySearch.put(searchId, futures);
        log.debug("Fake scheduler iniciado para {} ({} eventos agendados)", searchId, futures.size());
    }

    public void cancel(UUID searchId) {
        List<ScheduledFuture<?>> futures = scheduledBySearch.remove(searchId);
        if (futures == null) return;
        int cancelled = 0;
        for (ScheduledFuture<?> f : futures) {
            if (!f.isDone() && f.cancel(false)) cancelled++;
        }
        log.debug("Fake scheduler cancelado para {} ({} eventos restantes abortados)", searchId, cancelled);
    }

    private void emit(UUID searchId, Step step) {
        try {
            SearchProgressEvent event = SearchProgressEvent.syntheticSource(
                    searchId, step.source(), step.sourceStatus(),
                    step.fieldsExtracted(), step.progressPercent(), step.message());
            streamService.publish(event);
        } catch (RuntimeException e) {
            log.warn("Falha ao emitir evento sintético para {}: {}", searchId, e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}

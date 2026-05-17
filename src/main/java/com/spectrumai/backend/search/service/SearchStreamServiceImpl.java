package com.spectrumai.backend.search.service;

import com.spectrumai.backend.common.exception.ResourceNotFoundException;
import com.spectrumai.backend.search.dto.SearchProgressEvent;
import com.spectrumai.backend.search.model.Search;
import com.spectrumai.backend.search.model.SearchStatus;
import com.spectrumai.backend.search.repository.SearchRepository;
import com.spectrumai.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchStreamServiceImpl implements SearchStreamService {

    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    private final SearchRepository searchRepository;
    private final ConcurrentHashMap<UUID, Sinks.Many<SearchProgressEvent>> sinks = new ConcurrentHashMap<>();
    private final AtomicLong eventIdSeq = new AtomicLong();

    @Override
    @Transactional(readOnly = true)
    public Flux<ServerSentEvent<SearchProgressEvent>> subscribe(UUID searchId) {
        UUID tenantId = TenantContext.requireTenantId();
        Search search = searchRepository.findById(searchId)
                .orElseThrow(() -> new ResourceNotFoundException("Pesquisa não encontrada: " + searchId));
        if (!tenantId.equals(search.getTenant().getId())) {
            throw new ResourceNotFoundException("Pesquisa não encontrada: " + searchId);
        }

        if (search.getStatus() == SearchStatus.COMPLETED) {
            return Flux.just(toSse(SearchProgressEvent.completed(searchId, 0)));
        }
        if (search.getStatus() == SearchStatus.FAILED) {
            return Flux.just(toSse(SearchProgressEvent.failed(searchId, search.getFailureReason())));
        }

        Sinks.Many<SearchProgressEvent> sink = sinks.computeIfAbsent(searchId, k ->
                Sinks.many().multicast().onBackpressureBuffer(256, false));

        Flux<ServerSentEvent<SearchProgressEvent>> events = sink.asFlux().map(this::toSse);

        Flux<ServerSentEvent<SearchProgressEvent>> heartbeat = Flux.interval(HEARTBEAT_INTERVAL)
                .map(tick -> ServerSentEvent.<SearchProgressEvent>builder()
                        .comment("keepalive")
                        .build());

        SearchProgressEvent snapshot = search.getStatus() == SearchStatus.QUEUED
                ? SearchProgressEvent.queued(searchId)
                : SearchProgressEvent.processing(searchId);

        return Flux.<ServerSentEvent<SearchProgressEvent>>just(toSse(snapshot))
                .concatWith(Flux.merge(events, heartbeat))
                .takeUntil(sse -> sse.data() != null && sse.data().isTerminal())
                .timeout(STREAM_TIMEOUT)
                .doFinally(sig -> cleanupIfEmpty(searchId));
    }

    @Override
    public void publish(SearchProgressEvent event) {
        Sinks.Many<SearchProgressEvent> sink = sinks.get(event.searchId());
        if (sink == null) {
            return;
        }
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure()) {
            log.warn("Falha ao emitir evento {} para {}: {}", event.phase(), event.searchId(), result);
        }
        if (event.isTerminal()) {
            sink.tryEmitComplete();
            sinks.remove(event.searchId(), sink);
            log.debug("Sink encerrado para pesquisa {} (terminal={})", event.searchId(), event.phase());
        }
    }

    private ServerSentEvent<SearchProgressEvent> toSse(SearchProgressEvent event) {
        return ServerSentEvent.<SearchProgressEvent>builder()
                .id(String.valueOf(eventIdSeq.incrementAndGet()))
                .event(event.phase().toLowerCase())
                .data(event)
                .build();
    }

    private void cleanupIfEmpty(UUID searchId) {
        Sinks.Many<SearchProgressEvent> sink = sinks.get(searchId);
        if (sink != null && sink.currentSubscriberCount() == 0) {
            sinks.remove(searchId, sink);
        }
    }
}

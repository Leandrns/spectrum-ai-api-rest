package com.spectrumai.backend.search.service;

import com.spectrumai.backend.search.dto.SearchProgressEvent;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface SearchStreamService {

    /**
     * Assina o stream de eventos de progresso de uma pesquisa. Faz validação de
     * existência e tenant antes de retornar o Flux — pesquisa inexistente ou de
     * outro tenant lança {@code ResourceNotFoundException}.
     */
    Flux<ServerSentEvent<SearchProgressEvent>> subscribe(UUID searchId);

    /**
     * Publica um evento para todos os assinantes do searchId correspondente.
     * No-op silencioso quando não há assinantes ativos.
     */
    void publish(SearchProgressEvent event);
}

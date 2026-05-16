package com.spectrumai.backend.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrumai.backend.ai.dto.AiResponse;
import com.spectrumai.backend.ai.service.AiOrchestrationService;
import com.spectrumai.backend.search.dto.SearchRequest;
import com.spectrumai.backend.search.model.Search;
import com.spectrumai.backend.search.model.SearchStatus;
import com.spectrumai.backend.search.repository.SearchRepository;
import com.spectrumai.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;

/**
 * Executa a chamada ao provedor de IA fora da transação que enfileirou a
 * pesquisa. Roda em uma thread separada — o cliente recebe 202 ACCEPTED
 * imediatamente e acompanha o resultado via {@code GET /v1/searches/{id}/result}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchProcessor {

    private final SearchRepository searchRepository;
    private final AiOrchestrationService aiOrchestration;
    private final ObjectMapper objectMapper;

    @Async
    public void process(UUID searchId, UUID tenantId) {
        TenantContext.setTenantId(tenantId);
        try {
            markProcessing(searchId);
            try {
                executeAndPersist(searchId);
            } catch (RuntimeException e) {
                log.error("Falha na pesquisa {}: {}", searchId, e.getMessage(), e);
                markFailed(searchId, e.getMessage());
            }
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(UUID searchId) {
        searchRepository.findById(searchId).ifPresent(s -> {
            s.setStatus(SearchStatus.PROCESSING);
            searchRepository.save(s);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeAndPersist(UUID searchId) {
        Search search = searchRepository.findById(searchId)
                .orElseThrow(() -> new IllegalStateException("Pesquisa sumiu do banco: " + searchId));

        SearchRequest request = new SearchRequest(
                search.getBrand(),
                search.getModel(),
                search.getTrim(),
                search.getYear() == null ? null : search.getYear().intValue(),
                search.getCategories() == null ? java.util.List.of() : Arrays.asList(search.getCategories()),
                search.getSession() == null ? null : search.getSession().getId()
        );

        AiResponse response = aiOrchestration.runSpecSearch(request);
        JsonNode root = response.structuredOutput();

        JsonNode specsNode = root.path("specs");
        if (specsNode.isMissingNode() || specsNode.isNull()) {
            specsNode = root;
        }
        BigDecimal confidence = readConfidence(root);

        try {
            search.setSpecs(objectMapper.writeValueAsString(specsNode));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar specs retornadas pelo Gemini", e);
        }
        search.setConfidence(confidence);
        search.setStatus(SearchStatus.COMPLETED);
        search.setCompletedAt(OffsetDateTime.now());
        searchRepository.save(search);

        log.info("Pesquisa {} concluída em {}ms (confidence={})",
                searchId, response.latencyMs(), confidence);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID searchId, String reason) {
        searchRepository.findById(searchId).ifPresent(s -> {
            s.setStatus(SearchStatus.FAILED);
            s.setFailureReason(reason);
            s.setCompletedAt(OffsetDateTime.now());
            searchRepository.save(s);
        });
    }

    private BigDecimal readConfidence(JsonNode root) {
        JsonNode node = root.path("overallConfidence");
        if (node.isMissingNode() || node.isNull()) {
            node = root.path("confidence");
        }
        if (node.isNumber()) {
            return BigDecimal.valueOf(node.asDouble()).setScale(2, RoundingMode.HALF_UP);
        }
        return null;
    }
}

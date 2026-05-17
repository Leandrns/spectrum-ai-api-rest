package com.spectrumai.backend.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrumai.backend.ai.dto.AiResponse;
import com.spectrumai.backend.ai.service.AiOrchestrationService;
import com.spectrumai.backend.search.dto.SearchProgressEvent;
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
    private final SearchStreamService streamService;
    private final FakeProgressScheduler fakeProgressScheduler;

    @Async
    public void process(UUID searchId, UUID tenantId) {
        TenantContext.setTenantId(tenantId);
        try {
            markProcessing(searchId);
            streamService.publish(SearchProgressEvent.processing(searchId));
            fakeProgressScheduler.start(searchId);
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

        BigDecimal confidence = readConfidence(root);

        // Monta o JSON a persistir: categorias primeiro, sources sempre por último.
        // Se o modelo colocar "sources" dentro de "specs" (apesar do schema),
        // guardamos como fallback e ignoramos durante a cópia das categorias —
        // ObjectNode.set() só atualiza valor, não reordena, então pular aqui é
        // o que garante que sources fique no fim.
        com.fasterxml.jackson.databind.node.ObjectNode toSave = objectMapper.createObjectNode();
        JsonNode specsSourcesFallback = null;
        JsonNode specsNode = root.path("specs");
        if (!specsNode.isMissingNode() && !specsNode.isNull()) {
            var iterator = specsNode.fields();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if ("sources".equals(entry.getKey())) {
                    specsSourcesFallback = entry.getValue();
                } else {
                    toSave.set(entry.getKey(), entry.getValue());
                }
            }
        } else {
            // fallback: usa o root excluindo campos meta
            root.fields().forEachRemaining(e -> {
                if (!e.getKey().equals("overallConfidence") && !e.getKey().equals("sources")) {
                    toSave.set(e.getKey(), e.getValue());
                }
            });
        }
        JsonNode sourcesNode = root.path("sources");
        if (sourcesNode.isMissingNode() || sourcesNode.isNull()) {
            sourcesNode = specsSourcesFallback;
        }
        if (sourcesNode != null && !sourcesNode.isMissingNode() && !sourcesNode.isNull()) {
            toSave.set("sources", sourcesNode);
        }

        try {
            search.setSpecs(objectMapper.writeValueAsString(toSave));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar specs retornadas pelo Gemini", e);
        }
        search.setConfidence(confidence);
        search.setAiLatencyMs(response.latencyMs());
        search.setStatus(SearchStatus.COMPLETED);
        search.setCompletedAt(OffsetDateTime.now());
        searchRepository.save(search);

        int fieldsExtracted = countLeafFields(toSave);
        log.info("Pesquisa {} concluída em {}ms (confidence={}, fields={})",
                searchId, response.latencyMs(), confidence, fieldsExtracted);

        fakeProgressScheduler.cancel(searchId);
        streamService.publish(SearchProgressEvent.completed(searchId, fieldsExtracted));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID searchId, String reason) {
        searchRepository.findById(searchId).ifPresent(s -> {
            s.setStatus(SearchStatus.FAILED);
            s.setFailureReason(reason);
            s.setCompletedAt(OffsetDateTime.now());
            searchRepository.save(s);
        });
        fakeProgressScheduler.cancel(searchId);
        streamService.publish(SearchProgressEvent.failed(searchId, reason));
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

    /**
     * Conta recursivamente os campos folha não-nulos do JSON de specs. Usado para
     * informar o cliente, no evento COMPLETED, quantos campos a IA conseguiu preencher.
     * Ignora a entrada "sources" — não é uma especificação.
     */
    private int countLeafFields(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return 0;
        if (node.isValueNode()) return 1;
        int count = 0;
        if (node.isObject()) {
            var it = node.fields();
            while (it.hasNext()) {
                var entry = it.next();
                if ("sources".equals(entry.getKey())) continue;
                count += countLeafFields(entry.getValue());
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) count += countLeafFields(item);
        }
        return count;
    }
}

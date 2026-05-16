package com.spectrumai.backend.ai.service;

import com.spectrumai.backend.ai.dto.AiRequest;
import com.spectrumai.backend.ai.dto.AiResponse;
import com.spectrumai.backend.ai.prompt.PromptService;
import com.spectrumai.backend.ai.provider.AiProvider;
import com.spectrumai.backend.ai.provider.AiProviderResolver;
import com.spectrumai.backend.config.AppProperties;
import com.spectrumai.backend.search.dto.SearchRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiOrchestrationServiceImpl implements AiOrchestrationService {

    /** Nome do prompt versionado para pesquisa de specs (alimentado pela migration). */
    public static final String SPEC_SEARCH_PROMPT = "vehicle_spec_search";

    /** Nome do prompt versionado para geração de insights de sessão. */
    public static final String INSIGHTS_PROMPT = "session_insights";

    private final PromptService promptService;
    private final AiProviderResolver providerResolver;
    private final AppProperties properties;

    @Override
    public AiResponse runSpecSearch(SearchRequest request) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("brand", request.brand());
        variables.put("model", request.model());
        variables.put("trim", request.trim() == null ? "" : request.trim());
        variables.put("year", request.year() == null ? "" : request.year());
        variables.put("categories", String.join(", ", request.categories()));

        String prompt = promptService.render(SPEC_SEARCH_PROMPT, variables);

        AiRequest aiRequest = new AiRequest(
                prompt,
                null,
                properties.ai().gemini().groundingEnabled(),
                Map.of(
                        "operation", "spec_search",
                        "brand", request.brand(),
                        "model", request.model()
                )
        );

        AiProvider provider = providerResolver.resolveDefault();
        log.debug("Spec search via {} para {} {} {} ({})",
                provider.name(), request.brand(), request.model(), request.trim(), request.year());
        return provider.generate(aiRequest);
    }

    @Override
    public AiResponse runInsights(UUID sessionId) {
        // Implementação dedicada será adicionada junto ao InsightsService.
        throw new UnsupportedOperationException("runInsights ainda não implementado");
    }
}

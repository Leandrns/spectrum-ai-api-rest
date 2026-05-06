package com.spectrumai.backend.ai.provider;

import com.spectrumai.backend.ai.dto.AiRequest;
import com.spectrumai.backend.ai.dto.AiResponse;
import com.spectrumai.backend.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiAiProvider implements AiProvider {

    private final AppProperties properties;

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public AiResponse generate(AiRequest request) {
        // TODO: integração com Google Gemini API + Grounding nativo
        log.warn("GeminiAiProvider.generate() não implementado - retornando stub");
        throw new UnsupportedOperationException("Implementar chamada ao Gemini com Grounding ativado");
    }
}

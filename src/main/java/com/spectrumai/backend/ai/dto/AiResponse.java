package com.spectrumai.backend.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record AiResponse(
        JsonNode structuredOutput,
        List<Citation> citations,
        long latencyMs,
        int tokensUsed
) {
    public record Citation(String sourceUrl, String snippet) {}
}

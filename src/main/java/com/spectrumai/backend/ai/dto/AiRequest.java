package com.spectrumai.backend.ai.dto;

import java.util.Map;

public record AiRequest(
        String prompt,
        String responseSchemaJson,
        boolean groundingEnabled,
        Map<String, Object> metadata
) {}

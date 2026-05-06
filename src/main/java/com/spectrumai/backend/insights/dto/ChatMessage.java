package com.spectrumai.backend.insights.dto;

import java.time.OffsetDateTime;

public record ChatMessage(
        String role,
        String content,
        OffsetDateTime timestamp
) {}

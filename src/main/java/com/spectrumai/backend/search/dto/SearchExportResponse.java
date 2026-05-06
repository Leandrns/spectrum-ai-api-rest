package com.spectrumai.backend.search.dto;

import java.time.OffsetDateTime;

/** Resposta de {@code GET /v1/searches/&#123;id&#125;/export}. */
public record SearchExportResponse(
        String downloadUrl,
        OffsetDateTime expiresAt
) {}

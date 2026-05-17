package com.spectrumai.backend.search.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.spectrumai.backend.search.model.SearchStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Resposta de {@code GET /v1/searches/&#123;id&#125;/result}. */
public record SearchResultResponse(
        UUID searchId,
        VehicleSummary vehicle,
        SearchStatus status,
        OffsetDateTime completedAt,
        @JsonRawValue String specs,
        BigDecimal overallConfidence,
        Long aiLatencyMs
) {}

package com.spectrumai.backend.search.dto;

import com.spectrumai.backend.search.model.SearchStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Item da listagem de {@code GET /v1/searches}. */
public record SearchSummary(
        UUID searchId,
        VehicleSummary vehicle,
        SearchStatus status,
        OffsetDateTime completedAt
) {}

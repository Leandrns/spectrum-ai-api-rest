package com.spectrumai.backend.search.dto;

import com.spectrumai.backend.search.model.SearchStatus;

import java.util.UUID;

/** Resposta de {@code POST /v1/searches} (HTTP 202). */
public record SearchEnqueuedResponse(
        UUID searchId,
        SearchStatus status,
        int estimatedSeconds
) {}

package com.spectrumai.backend.search.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** Payload de {@code POST /v1/searches}. */
public record SearchRequest(
        @NotBlank String brand,
        @NotBlank String model,
        String trim,
        @Min(1990) @Max(2100) Integer year,
        @NotEmpty List<String> categories,
        UUID sessionId
) {}

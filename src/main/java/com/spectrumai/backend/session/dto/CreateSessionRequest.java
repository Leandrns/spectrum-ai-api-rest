package com.spectrumai.backend.session.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSessionRequest(
        @NotBlank String name,
        String description
) {}

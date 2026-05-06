package com.spectrumai.backend.auth.dto;

/** Resposta de {@code POST /v1/auth/refresh}. */
public record RefreshResponse(
        String accessToken,
        long expiresIn
) {}

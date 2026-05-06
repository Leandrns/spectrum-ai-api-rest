package com.spectrumai.backend.auth.dto;

/** Resposta padrão de login/registro do contrato. */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        AuthenticatedUser user
) {}

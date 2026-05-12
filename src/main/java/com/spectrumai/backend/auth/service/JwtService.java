package com.spectrumai.backend.auth.service;

import com.spectrumai.backend.auth.security.UserPrincipal;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.UUID;

public interface JwtService {

    /** Gera um access token JWT com claims do principal. */
    String generateAccessToken(UserPrincipal principal);

    /** Gera um refresh token JWT (TTL maior, claim {@code type=REFRESH}). */
    String generateRefreshToken(UserPrincipal principal);

    /**
     * Valida assinatura/expiração de um access token e retorna a {@link Authentication}
     * pronta para o {@code SecurityContextHolder}. Retorna vazio se inválido.
     */
    Optional<Authentication> parseAuthentication(String token);

    /**
     * Valida um refresh token e retorna o {@code userId} (subject).
     * Retorna vazio se o token for inválido, expirado ou não for do tipo {@code REFRESH}.
     */
    Optional<UUID> parseRefreshTokenUserId(String token);
}

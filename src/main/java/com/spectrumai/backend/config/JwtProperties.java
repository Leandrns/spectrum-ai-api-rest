package com.spectrumai.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Convenção do contrato:
 * <ul>
 *   <li>{@code expirationMs}: validade do access token em milissegundos
 *       (env: {@code JWT_EXPIRATION_MS}, default 1h).</li>
 *   <li>{@code refreshExpirationMs}: validade do refresh token em ms
 *       (env: {@code JWT_REFRESH_EXPIRATION_MS}, default 7 dias).</li>
 *   <li>O campo {@code expiresIn} retornado pela API é em segundos.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "spectrum.security.jwt")
public record JwtProperties(
        String secret,
        long expirationMs,
        long refreshExpirationMs,
        String issuer
) {
    public long expiresInSeconds() {
        return expirationMs / 1000;
    }
}

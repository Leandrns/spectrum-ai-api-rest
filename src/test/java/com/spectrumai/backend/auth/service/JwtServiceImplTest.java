package com.spectrumai.backend.auth.service;

import com.spectrumai.backend.auth.security.UserPrincipal;
import com.spectrumai.backend.config.JwtProperties;
import com.spectrumai.backend.user.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JwtServiceImplTest} cobre a geração e validação de tokens JWT em
 * isolamento (sem contexto Spring): assinatura HS256, expiração, distinção
 * entre access/refresh token e rejeição de tokens adulterados ou emitidos
 * por outro issuer.
 */
class JwtServiceImplTest {

    private static final String SECRET = "unit-test-jwt-secret-key-com-pelo-menos-32-bytes!!";

    private final JwtProperties properties =
            new JwtProperties(SECRET, 3_600_000L, 604_800_000L, "spectrum-ai-test");
    private final JwtServiceImpl jwtService = new JwtServiceImpl(properties);

    private UserPrincipal principal() {
        return new UserPrincipal(UUID.randomUUID(), UUID.randomUUID(), "ana@empresa.com", Role.ANALYST);
    }

    @Test
    @DisplayName("access token gerado é válido e carrega o papel do usuário como authority")
    void generatesAndParsesAccessToken() {
        UserPrincipal principal = principal();
        String token = jwtService.generateAccessToken(principal);

        Optional<Authentication> auth = jwtService.parseAuthentication(token);

        assertThat(auth).isPresent();
        assertThat(auth.get().getPrincipal()).isEqualTo(principal);
        assertThat(auth.get().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ANALYST");
    }

    @Test
    @DisplayName("refresh token não é aceito como access token")
    void refreshTokenIsRejectedAsAccessToken() {
        String refreshToken = jwtService.generateRefreshToken(principal());

        assertThat(jwtService.parseAuthentication(refreshToken)).isEmpty();
    }

    @Test
    @DisplayName("refresh token válido devolve o userId do subject")
    void parsesRefreshTokenUserId() {
        UserPrincipal principal = principal();
        String refreshToken = jwtService.generateRefreshToken(principal);

        assertThat(jwtService.parseRefreshTokenUserId(refreshToken)).contains(principal.userId());
    }

    @Test
    @DisplayName("access token não é aceito como refresh token")
    void accessTokenIsRejectedAsRefreshToken() {
        String accessToken = jwtService.generateAccessToken(principal());

        assertThat(jwtService.parseRefreshTokenUserId(accessToken)).isEmpty();
    }

    @Test
    @DisplayName("token expirado é rejeitado")
    void expiredTokenIsRejected() {
        JwtProperties expiredProps = new JwtProperties(SECRET, -1_000L, -1_000L, "spectrum-ai-test");
        JwtServiceImpl expiredService = new JwtServiceImpl(expiredProps);
        String token = expiredService.generateAccessToken(principal());

        assertThat(jwtService.parseAuthentication(token)).isEmpty();
    }

    @Test
    @DisplayName("token com assinatura adulterada é rejeitado")
    void tamperedTokenIsRejected() {
        String token = jwtService.generateAccessToken(principal());
        // Adultera o último caractere da assinatura (após o último ponto).
        int lastDot = token.lastIndexOf('.');
        char lastChar = token.charAt(token.length() - 1);
        char replacement = lastChar == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, token.length() - 1) + replacement;
        assertThat(lastDot).isPositive();

        assertThat(jwtService.parseAuthentication(tampered)).isEmpty();
    }

    @Test
    @DisplayName("token assinado com outro issuer é rejeitado")
    void tokenFromDifferentIssuerIsRejected() {
        JwtProperties otherIssuer = new JwtProperties(SECRET, 3_600_000L, 604_800_000L, "outro-issuer");
        JwtServiceImpl otherService = new JwtServiceImpl(otherIssuer);
        String token = otherService.generateAccessToken(principal());

        assertThat(jwtService.parseAuthentication(token)).isEmpty();
    }

    @Test
    @DisplayName("token vazio ou malformado não derruba o serviço")
    void malformedTokenIsRejectedGracefully() {
        assertThat(jwtService.parseAuthentication("nao-e-um-jwt")).isEmpty();
        assertThat(jwtService.parseAuthentication("")).isEmpty();
    }
}

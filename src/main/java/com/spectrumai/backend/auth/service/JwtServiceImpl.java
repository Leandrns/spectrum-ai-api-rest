package com.spectrumai.backend.auth.service;

import com.spectrumai.backend.auth.security.UserPrincipal;
import com.spectrumai.backend.config.JwtProperties;
import com.spectrumai.backend.user.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementação do {@link JwtService} usando jjwt 0.12.
 *
 * <p>Tokens HS256 com claims: {@code sub} (userId), {@code tenantId},
 * {@code email}, {@code role}, {@code type} (ACCESS|REFRESH).</p>
 */
@Slf4j
@Service
public class JwtServiceImpl implements JwtService {

    private static final String CLAIM_TENANT = "tenantId";
    private static final String CLAIM_EMAIL  = "email";
    private static final String CLAIM_ROLE   = "role";
    private static final String CLAIM_TYPE   = "type";
    private static final String TYPE_ACCESS  = "ACCESS";
    private static final String TYPE_REFRESH = "REFRESH";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtServiceImpl(JwtProperties properties) {
        this.properties = properties;
        // Em HS256 o jjwt exige chave de >= 256 bits (>= 32 bytes ASCII)
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String generateAccessToken(UserPrincipal principal) {
        return buildToken(principal, TYPE_ACCESS, properties.expirationMs());
    }

    @Override
    public String generateRefreshToken(UserPrincipal principal) {
        return buildToken(principal, TYPE_REFRESH, properties.refreshExpirationMs());
    }

    @Override
    public Optional<Authentication> parseAuthentication(String token) {
        return parseClaims(token).flatMap(claims -> {
            if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
                return Optional.empty();
            }
            UserPrincipal principal = new UserPrincipal(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.get(CLAIM_TENANT, String.class)),
                    claims.get(CLAIM_EMAIL, String.class),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class))
            );
            List<SimpleGrantedAuthority> authorities =
                    List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()));
            return Optional.of(new UsernamePasswordAuthenticationToken(principal, null, authorities));
        });
    }

    @Override
    public Optional<UUID> parseRefreshTokenUserId(String token) {
        return parseClaims(token).flatMap(claims -> {
            if (!TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class))) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(claims.getSubject()));
        });
    }

    private String buildToken(UserPrincipal principal, String type, long ttlMs) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(principal.userId().toString())
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ttlMs)))
                .claim(CLAIM_TENANT, principal.tenantId().toString())
                .claim(CLAIM_EMAIL, principal.email())
                .claim(CLAIM_ROLE, principal.role().name())
                .claim(CLAIM_TYPE, type)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    private Optional<Claims> parseClaims(String token) {
        try {
            Jws<Claims> parsed = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token);
            return Optional.of(parsed.getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token JWT inválido: {}", e.getMessage());
            return Optional.empty();
        }
    }
}

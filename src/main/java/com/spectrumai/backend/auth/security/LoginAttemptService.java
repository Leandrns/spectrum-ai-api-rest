package com.spectrumai.backend.auth.security;

import com.spectrumai.backend.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rastreia tentativas de login falhas por e-mail + IP. Aplica lockout
 * temporario quando o numero de falhas excede {@code lockout.max-failures}
 * dentro da janela {@code lockout.window-seconds}.
 *
 * <p>In-memory por design (deploy single-instance). Em producao multi-replica
 * usar Redis com TTL.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final SecurityProperties securityProperties;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String email, String ip) {
        if (!securityProperties.lockout().enabled()) {
            return false;
        }
        Attempt a = attempts.get(key(email, ip));
        if (a == null) return false;
        if (isExpired(a)) {
            attempts.remove(key(email, ip));
            return false;
        }
        return a.failures >= securityProperties.lockout().maxFailures();
    }

    public void recordFailure(String email, String ip) {
        String k = key(email, ip);
        Attempt updated = attempts.compute(k, (key, current) -> {
            if (current == null || isExpired(current)) {
                return new Attempt(1, Instant.now());
            }
            return new Attempt(current.failures + 1, current.firstFailureAt);
        });
        log.warn("AUTH_FAIL email={} ip={} failures={} window_until={}",
                mask(email), ip, updated.failures,
                updated.firstFailureAt.plusSeconds(securityProperties.lockout().windowSeconds()));
    }

    public void recordSuccess(String email, String ip) {
        attempts.remove(key(email, ip));
    }

    private boolean isExpired(Attempt a) {
        Duration window = Duration.ofSeconds(securityProperties.lockout().windowSeconds());
        return Instant.now().isAfter(a.firstFailureAt.plus(window));
    }

    private String key(String email, String ip) {
        return (email == null ? "" : email.toLowerCase()) + "|" + (ip == null ? "" : ip);
    }

    /** Mascara o e-mail no log: jo***@dominio.com. */
    private String mask(String email) {
        if (email == null) return "?";
        int at = email.indexOf('@');
        if (at <= 2) return "***" + (at >= 0 ? email.substring(at) : "");
        return email.substring(0, 2) + "***" + email.substring(at);
    }

    private record Attempt(int failures, Instant firstFailureAt) {}
}

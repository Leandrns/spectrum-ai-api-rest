package com.spectrumai.backend.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrumai.backend.common.dto.ApiError;
import com.spectrumai.backend.common.exception.ErrorCode;
import com.spectrumai.backend.config.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Rate limit por IP usando algoritmo token-bucket in-memory.
 *
 * <p>Endpoints {@code /v1/auth/**} usam o limite mais restrito (anti
 * brute-force). Demais endpoints usam o limite padrao.</p>
 *
 * <p>Para deploy multi-instancia, substituir o map por Redis (ex.: bucket4j-redis).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;
    private final Map<String, AtomicReference<Bucket>> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (!securityProperties.rateLimit().enabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        // CORS preflight nao entra na contagem
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        int limit = path.startsWith("/v1/auth")
                ? securityProperties.rateLimit().authPerMinute()
                : securityProperties.rateLimit().defaultPerMinute();

        String key = clientIp(request) + "|" + (path.startsWith("/v1/auth") ? "auth" : "default");
        if (!tryConsume(key, limit)) {
            log.warn("RATE_LIMIT_EXCEEDED ip={} path={} limit={}/min", clientIp(request), path, limit);
            writeRateLimitResponse(response, request, limit);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean tryConsume(String key, int capacityPerMinute) {
        long now = System.nanoTime();
        AtomicReference<Bucket> ref = buckets.computeIfAbsent(key,
                k -> new AtomicReference<>(new Bucket(capacityPerMinute, now)));

        while (true) {
            Bucket current = ref.get();
            // Refill: capacityPerMinute tokens / 60s
            double elapsedSec = (now - current.lastRefillNanos()) / 1_000_000_000.0;
            double refill = elapsedSec * (capacityPerMinute / 60.0);
            double newTokens = Math.min(capacityPerMinute, current.tokens() + refill);

            if (newTokens < 1.0) {
                return false;
            }

            Bucket next = new Bucket(newTokens - 1.0, now);
            if (ref.compareAndSet(current, next)) {
                return true;
            }
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeRateLimitResponse(HttpServletResponse response, HttpServletRequest request, int limit)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60");
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        ApiError body = new ApiError(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                ErrorCode.RATE_LIMITED,
                "Limite de requisicoes excedido. Tente novamente em instantes.",
                OffsetDateTime.now(),
                request.getRequestURI()
        );
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private record Bucket(double tokens, long lastRefillNanos) {}
}

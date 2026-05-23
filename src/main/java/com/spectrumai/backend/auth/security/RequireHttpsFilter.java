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

/**
 * For�a HTTPS quando {@code spectrum.security.require-https=true}. Substitui
 * o {@code HttpSecurity.requiresChannel(...)} que foi removido no Spring
 * Security 7 (Spring Boot 4) junto com {@code ChannelDecisionManager}.
 *
 * <p>Detecta o esquema usando, em ordem:</p>
 * <ol>
 *   <li>{@code X-Forwarded-Proto} (set pelo proxy reverso � Render, nginx, ALB)</li>
 *   <li>{@code request.isSecure()} (TLS direto na JVM)</li>
 * </ol>
 *
 * <p>Health-checks do Actuator passam direto (proxy precisa probar sem TLS).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequireHttpsFilter extends OncePerRequestFilter {

    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (!securityProperties.requireHttps()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (path != null && path.startsWith("/actuator/")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isSecure(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("HTTPS_REQUIRED path={} remote={} proto={}",
                path, request.getRemoteAddr(), request.getHeader("X-Forwarded-Proto"));

        response.setStatus(HttpStatus.UPGRADE_REQUIRED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = new ApiError(
                HttpStatus.UPGRADE_REQUIRED.value(),
                ErrorCode.FORBIDDEN,
                "HTTPS � obrigat�rio.",
                OffsetDateTime.now(),
                path
        );
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private boolean isSecure(HttpServletRequest request) {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null && !forwardedProto.isBlank()) {
            // Pode vir como "https, http" se houver chain de proxies; pega o primeiro.
            String first = forwardedProto.split(",")[0].trim();
            return "https".equalsIgnoreCase(first);
        }
        return request.isSecure();
    }
}

package com.spectrumai.backend.auth.security;

import com.spectrumai.backend.auth.service.JwtService;
import com.spectrumai.backend.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    // Endpoints SSE (ex.: /v1/searches/{id}/stream) retornam Flux e disparam um
    // ASYNC dispatch ao concluir. Por padrao OncePerRequestFilter pula esse
    // dispatch, deixando o SecurityContext vazio — mas o AuthorizationFilter do
    // Spring Security roda mesmo assim e nega acesso, gerando logs de erro
    // ("response is already committed") apos cada stream concluida. Re-rodar
    // este filtro no async dispatch re-popula a auth pelo Bearer token.
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                jwtService.parseAuthentication(token).ifPresent(auth -> {
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    if (auth.getPrincipal() instanceof UserPrincipal principal) {
                        TenantContext.setTenantId(principal.tenantId());
                    }
                });
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}

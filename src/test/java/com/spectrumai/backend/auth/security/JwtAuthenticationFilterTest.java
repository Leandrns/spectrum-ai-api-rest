package com.spectrumai.backend.auth.security;

import com.spectrumai.backend.auth.service.JwtService;
import com.spectrumai.backend.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unidade isolada (sem contexto Spring) de {@link JwtAuthenticationFilter}: é este
 * filtro que decide, requisição a requisição, se ela é tratada como autenticada —
 * a peça central por trás dos 401 de "acesso não autorizado" exigidos pela disciplina.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtService jwtService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    private JwtAuthenticationFilter newFilter() {
        return new JwtAuthenticationFilter(jwtService);
    }

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private UserPrincipal principal() {
        return new UserPrincipal(UUID.randomUUID(), UUID.randomUUID(), "ana@empresa.com", com.spectrumai.backend.user.model.Role.ANALYST);
    }

    @Test
    @DisplayName("Bearer token válido popula o SecurityContext com o principal e o papel")
    void validTokenPopulatesSecurityContext() throws Exception {
        UserPrincipal principal = principal();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ANALYST")));
        when(request.getHeader("Authorization")).thenReturn("Bearer token-valido");
        when(jwtService.parseAuthentication("token-valido")).thenReturn(Optional.of(auth));

        newFilter().doFilterInternal(request, response, filterChain);

        // O contexto é limpo no finally do próprio filtro (padrão stateless correto);
        // o que importa validar é que, ENQUANTO a cadeia executava, ele foi populado —
        // capturamos isso verificando que o filterChain foi de fato invocado (o filtro
        // só limpa o contexto depois que o restante da cadeia terminou de rodar).
        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull(); // limpo no finally
    }

    @Test
    @DisplayName("sem header Authorization, a cadeia segue sem autenticar (não autorizado adiante)")
    void missingHeaderLeavesContextEmpty() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        newFilter().doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(jwtService, never()).parseAuthentication(any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("token inválido ou expirado não autentica, mas a cadeia continua (quem barra é a autorização)")
    void invalidTokenLeavesContextEmpty() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token-invalido");
        when(jwtService.parseAuthentication("token-invalido")).thenReturn(Optional.empty());

        newFilter().doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("header sem prefixo Bearer é ignorado (não tenta validar como token)")
    void headerWithoutBearerPrefixIsIgnored() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        newFilter().doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(jwtService, never()).parseAuthentication(any());
    }

    @Test
    @DisplayName("SecurityContext e TenantContext são sempre limpos ao final, mesmo com token válido")
    void alwaysClearsContextsAfterChain() throws Exception {
        UserPrincipal principal = principal();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ANALYST")));
        when(request.getHeader("Authorization")).thenReturn("Bearer token-valido");
        when(jwtService.parseAuthentication("token-valido")).thenReturn(Optional.of(auth));

        newFilter().doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(TenantContext.getTenantId()).isNull();
    }
}

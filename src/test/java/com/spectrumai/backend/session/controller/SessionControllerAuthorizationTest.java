package com.spectrumai.backend.session.controller;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Avalia diretamente as expressões {@code @PreAuthorize} de {@link SessionController}
 * contra os três papéis do sistema (ADMIN/ANALYST/VIEWER) e contra um usuário anônimo —
 * cobrindo "acesso não autorizado" no nível de autorização (403), em complemento a
 * {@link com.spectrumai.backend.auth.security.JwtAuthenticationFilterTest}, que cobre
 * autenticação (401).
 *
 * <p>Evita subir o contexto Spring/servlet: avalia a SpEL de cada anotação com o mesmo
 * motor (Spring Security) que o {@code @EnableMethodSecurity} da aplicação usa em
 * produção, então testa a regra de negócio real, não uma reimplementação dela.</p>
 */
class SessionControllerAuthorizationTest {

    private final DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
    private final ExpressionParser parser = handler.getExpressionParser();

    private Authentication authFor(String... roles) {
        List<GrantedAuthority> authorities = List.of(roles).stream()
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return new UsernamePasswordAuthenticationToken("user@empresa.com", null, authorities);
    }

    private Authentication anonymous() {
        return new AnonymousAuthenticationToken("key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
    }

    private boolean evaluate(Method method, Authentication authentication) {
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).as("método %s deveria ter @PreAuthorize", method.getName()).isNotNull();

        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getMethod()).thenReturn(method);
        when(invocation.getArguments()).thenReturn(new Object[method.getParameterCount()]);

        Expression expression = parser.parseExpression(preAuthorize.value());
        EvaluationContext context = handler.createEvaluationContext(authentication, invocation);
        return Boolean.TRUE.equals(expression.getValue(context, Boolean.class));
    }

    private Method method(String name, Class<?>... paramTypes) throws NoSuchMethodException {
        return SessionController.class.getMethod(name, paramTypes);
    }

    @Test
    @DisplayName("criar sessão: ADMIN e ANALYST podem, VIEWER e anônimo não podem")
    void createSessionRoles() throws Exception {
        Method create = method("create", com.spectrumai.backend.session.dto.CreateSessionRequest.class);

        assertThat(evaluate(create, authFor("ADMIN"))).isTrue();
        assertThat(evaluate(create, authFor("ANALYST"))).isTrue();
        assertThat(evaluate(create, authFor("VIEWER"))).isFalse();
        assertThat(evaluate(create, anonymous())).isFalse();
    }

    @Test
    @DisplayName("listar/consultar sessão: os três papéis podem, anônimo não pode")
    void readSessionRoles() throws Exception {
        Method getById = method("getById", java.util.UUID.class);
        Method list = method("list", org.springframework.data.domain.Pageable.class);

        for (Method m : List.of(getById, list)) {
            assertThat(evaluate(m, authFor("ADMIN"))).as(m.getName() + " ADMIN").isTrue();
            assertThat(evaluate(m, authFor("ANALYST"))).as(m.getName() + " ANALYST").isTrue();
            assertThat(evaluate(m, authFor("VIEWER"))).as(m.getName() + " VIEWER").isTrue();
            assertThat(evaluate(m, anonymous())).as(m.getName() + " anônimo").isFalse();
        }
    }

    @Test
    @DisplayName("exportar sessão (arquivo ou BigQuery): ADMIN e ANALYST podem, VIEWER não pode")
    void exportSessionRoles() throws Exception {
        Method export = method("export", java.util.UUID.class, String.class);
        Method exportBigQuery = method("exportToBigQuery", java.util.UUID.class);

        for (Method m : List.of(export, exportBigQuery)) {
            assertThat(evaluate(m, authFor("ADMIN"))).as(m.getName() + " ADMIN").isTrue();
            assertThat(evaluate(m, authFor("ANALYST"))).as(m.getName() + " ANALYST").isTrue();
            assertThat(evaluate(m, authFor("VIEWER"))).as(m.getName() + " VIEWER").isFalse();
        }
    }
}

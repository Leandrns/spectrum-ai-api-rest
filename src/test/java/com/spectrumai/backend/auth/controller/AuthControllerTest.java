package com.spectrumai.backend.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrumai.backend.auth.dto.AuthResponse;
import com.spectrumai.backend.auth.dto.AuthenticatedUser;
import com.spectrumai.backend.auth.dto.RefreshResponse;
import com.spectrumai.backend.auth.service.AuthService;
import com.spectrumai.backend.auth.service.JwtService;
import com.spectrumai.backend.common.exception.BusinessException;
import com.spectrumai.backend.common.exception.ErrorCode;
import com.spectrumai.backend.config.SecurityProperties;
import com.spectrumai.backend.user.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa {@code /v1/auth/**} na fatia web (controller + validação Bean Validation
 * + {@link com.spectrumai.backend.common.exception.GlobalExceptionHandler}), com
 * {@link AuthService} mockado. A cadeia de segurança real (JWT, papéis) é coberta
 * separadamente em {@code SecurityAccessControlTest}, já que estes endpoints são
 * públicos por contrato.
 */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = {
        "spectrum.security.require-https=false",
        "spectrum.security.rate-limit.enabled=false",
        "spectrum.security.rate-limit.default-per-minute=1000",
        "spectrum.security.rate-limit.auth-per-minute=1000",
        "spectrum.security.lockout.enabled=false",
        "spectrum.security.lockout.max-failures=5",
        "spectrum.security.lockout.window-seconds=900"
})
@Import(AuthControllerTest.JacksonTestConfig.class)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private AuthService authService;
    // JwtAuthenticationFilter é um @Component (Filter) e o @WebMvcTest o detecta
    // automaticamente mesmo com addFilters=false; precisa de um JwtService para
    // instanciar, mesmo sem ser exercitado nestes testes (endpoints públicos).
    @MockitoBean private JwtService jwtService;

    private AuthResponse sampleAuthResponse() {
        return new AuthResponse(
                "access-token", "refresh-token", 3600L,
                new AuthenticatedUser(UUID.randomUUID(), "Ana Souza", "ana@empresa.com", Role.ANALYST, UUID.randomUUID()));
    }

    @Test
    @DisplayName("login com credenciais válidas devolve 200 e os tokens")
    void loginSuccessReturns200() throws Exception {
        when(authService.login(any())).thenReturn(sampleAuthResponse());

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ana@empresa.com","password":"Spectrum#2026Xk"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.user.role").value("ANALYST"));
    }

    @Test
    @DisplayName("login com corpo inválido devolve 400 com os campos que falharam")
    void loginWithInvalidBodyReturns400() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nao-e-email","password":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(ErrorCode.VALIDATION_ERROR))
                .andExpect(jsonPath("$.fields", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
    }

    @Test
    @DisplayName("login com credenciais inválidas repassa o 401 do service via GlobalExceptionHandler")
    void loginWithWrongCredentialsReturns401() throws Exception {
        when(authService.login(any()))
                .thenThrow(new BusinessException("Credenciais inválidas", HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED));

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ana@empresa.com","password":"SenhaErrada#1"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("register com e-mail duplicado devolve 409")
    void registerWithDuplicateEmailReturns409() throws Exception {
        when(authService.register(any()))
                .thenThrow(new BusinessException("E-mail já cadastrado", HttpStatus.CONFLICT, ErrorCode.VALIDATION_ERROR));

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Empresa Teste","fullName":"Ana Souza",
                                 "email":"ana@empresa.com","password":"Spectrum#2026Xk"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("register com senha fraca devolve 400 sem chegar ao service")
    void registerWithWeakPasswordReturns400() throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Empresa Teste","fullName":"Ana Souza",
                                 "email":"ana@empresa.com","password":"123"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("refresh com token válido devolve novo access token")
    void refreshSuccessReturns200() throws Exception {
        when(authService.refresh("refresh-token-valido")).thenReturn(new RefreshResponse("novo-access-token", 3600L));

        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"refresh-token-valido"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("novo-access-token"));
    }

    @Test
    @DisplayName("refresh sem token no corpo devolve 400")
    void refreshWithBlankTokenReturns400() throws Exception {
        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":""}"""))
                .andExpect(status().isBadRequest());
    }

    /**
     * Combinar {@code @EnableConfigurationProperties} com {@code @WebMvcTest} nesta
     * versão do Spring Boot deixa de puxar o {@code JacksonAutoConfiguration} padrão;
     * fornece o {@link ObjectMapper} explicitamente para os filtros de segurança
     * auto-detectados (RateLimitFilter/RequireHttpsFilter) e para o próprio MockMvc.
     */
    @TestConfiguration
    static class JacksonTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}

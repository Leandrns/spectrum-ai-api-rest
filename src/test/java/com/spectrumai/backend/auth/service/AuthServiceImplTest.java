package com.spectrumai.backend.auth.service;

import com.spectrumai.backend.audit.AuditService;
import com.spectrumai.backend.auth.dto.AuthResponse;
import com.spectrumai.backend.auth.dto.LoginRequest;
import com.spectrumai.backend.auth.dto.RegisterRequest;
import com.spectrumai.backend.auth.security.LoginAttemptService;
import com.spectrumai.backend.auth.security.UserPrincipal;
import com.spectrumai.backend.common.exception.BusinessException;
import com.spectrumai.backend.company.model.Company;
import com.spectrumai.backend.company.repository.CompanyRepository;
import com.spectrumai.backend.config.JwtProperties;
import com.spectrumai.backend.user.model.Role;
import com.spectrumai.backend.user.model.User;
import com.spectrumai.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link AuthServiceImpl} nos cenários de sucesso, erro e bloqueio
 * exigidos para a disciplina de SOA (login, cadastro e refresh de token),
 * isolando repositórios e colaboradores via Mockito.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String RAW_PASSWORD = "Spectrum#2026Xk";

    @Mock private UserRepository userRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private AuditService auditService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties("test-secret", 3_600_000L, 604_800_000L, "spectrum-ai-test");
        authService = new AuthServiceImpl(
                userRepository, companyRepository, passwordEncoder,
                jwtService, jwtProperties, loginAttemptService, auditService);
    }

    private User activeUser(Role role) {
        Company tenant = Company.builder().id(UUID.randomUUID()).name("Empresa Teste").active(true).build();
        return User.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .fullName("Ana Souza")
                .email("ana@empresa.com")
                .passwordHash("hash")
                .role(role)
                .active(true)
                .build();
    }

    // ---------- login ----------

    @Test
    @DisplayName("login com credenciais corretas devolve tokens e marca sucesso")
    void loginSucceeds() {
        User user = activeUser(Role.ANALYST);
        when(loginAttemptService.isBlocked(anyString(), anyString())).thenReturn(false);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches(RAW_PASSWORD, user.getPasswordHash())).thenReturn(true);
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");

        AuthResponse response = authService.login(new LoginRequest(user.getEmail(), RAW_PASSWORD));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().role()).isEqualTo(Role.ANALYST);
        verify(loginAttemptService).recordSuccess(user.getEmail(), "unknown");
        verify(loginAttemptService, never()).recordFailure(anyString(), anyString());
    }

    @Test
    @DisplayName("login com senha incorreta é rejeitado com 401 e registra a falha")
    void loginWithWrongPasswordIsUnauthorized() {
        User user = activeUser(Role.ANALYST);
        when(loginAttemptService.isBlocked(anyString(), anyString())).thenReturn(false);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("senha-errada", user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), "senha-errada")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(loginAttemptService).recordFailure(user.getEmail(), "unknown");
    }

    @Test
    @DisplayName("login com e-mail inexistente é rejeitado com 401, sem vazar qual campo errou")
    void loginWithUnknownEmailIsUnauthorized() {
        when(loginAttemptService.isBlocked(anyString(), anyString())).thenReturn(false);
        when(userRepository.findByEmail("fantasma@empresa.com")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("fantasma@empresa.com", RAW_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("login de usuário inativo é rejeitado com 401")
    void loginWithInactiveUserIsUnauthorized() {
        User user = activeUser(Role.ANALYST);
        user.setActive(false);
        when(loginAttemptService.isBlocked(anyString(), anyString())).thenReturn(false);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(java.util.Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), RAW_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("login bloqueado por excesso de tentativas devolve 429 sem tocar no repositório")
    void loginBlockedByLockoutReturnsTooManyRequests() {
        when(loginAttemptService.isBlocked("ana@empresa.com", "unknown")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ana@empresa.com", RAW_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(userRepository, never()).findByEmail(anyString());
    }

    // ---------- register ----------

    @Test
    @DisplayName("cadastro com e-mail já existente é rejeitado com 409")
    void registerWithExistingEmailIsConflict() {
        when(userRepository.existsByEmail("ana@empresa.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Empresa Teste", "Ana Souza", "ana@empresa.com", RAW_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("cadastro com senha contendo o nome do usuário é rejeitado com 400")
    void registerWithPasswordContainingNameIsBadRequest() {
        when(userRepository.existsByEmail("ana@empresa.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Empresa Teste", "Souza", "ana@empresa.com", "Souza#2026Xk")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("cadastro de nova empresa cria o usuário como ADMIN")
    void registerCreatesNewTenantAndAdminUser() {
        when(userRepository.existsByEmail("ana@empresa.com")).thenReturn(false);
        when(companyRepository.existsByName("Empresa Teste")).thenReturn(false);
        when(companyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn("hash");
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");

        AuthResponse response = authService.register(
                new RegisterRequest("Empresa Teste", "Ana Souza", "ana@empresa.com", RAW_PASSWORD));

        assertThat(response.user().role()).isEqualTo(Role.ADMIN);
        assertThat(response.user().email()).isEqualTo("ana@empresa.com");
    }

    // ---------- registerAnalyst ----------

    @Test
    @DisplayName("cadastro de analista numa empresa inexistente é rejeitado com 404")
    void registerAnalystWithUnknownCompanyIsNotFound() {
        when(userRepository.existsByEmail("ana@empresa.com")).thenReturn(false);
        when(companyRepository.existsByName("Empresa Fantasma")).thenReturn(false);

        assertThatThrownBy(() -> authService.registerAnalyst(
                new RegisterRequest("Empresa Fantasma", "Ana Souza", "ana@empresa.com", RAW_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("cadastro de analista numa empresa existente vincula o usuário ao tenant correto")
    void registerAnalystLinksToExistingTenant() {
        Company tenant = Company.builder().id(UUID.randomUUID()).name("Empresa Teste").active(true).build();
        when(userRepository.existsByEmail("ana@empresa.com")).thenReturn(false);
        when(companyRepository.existsByName("Empresa Teste")).thenReturn(true);
        when(companyRepository.findByName("Empresa Teste")).thenReturn(tenant);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn("hash");
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");

        AuthResponse response = authService.registerAnalyst(
                new RegisterRequest("Empresa Teste", "Ana Souza", "ana@empresa.com", RAW_PASSWORD));

        assertThat(response.user().role()).isEqualTo(Role.ANALYST);
        assertThat(response.user().tenantId()).isEqualTo(tenant.getId());
        verify(companyRepository, never()).save(any());
    }

    // ---------- refresh ----------

    @Test
    @DisplayName("refresh com token válido gera novo access token")
    void refreshSucceeds() {
        User user = activeUser(Role.VIEWER);
        when(jwtService.parseRefreshTokenUserId("refresh-token")).thenReturn(java.util.Optional.of(user.getId()));
        when(userRepository.findById(user.getId())).thenReturn(java.util.Optional.of(user));
        when(jwtService.generateAccessToken(any())).thenReturn("novo-access-token");

        var response = authService.refresh("refresh-token");

        assertThat(response.accessToken()).isEqualTo("novo-access-token");
    }

    @Test
    @DisplayName("refresh com token inválido é rejeitado com 401")
    void refreshWithInvalidTokenIsUnauthorized() {
        when(jwtService.parseRefreshTokenUserId("token-invalido")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> authService.refresh("token-invalido"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("refresh de usuário inativo é rejeitado com 401")
    void refreshWithInactiveUserIsUnauthorized() {
        User user = activeUser(Role.VIEWER);
        user.setActive(false);
        when(jwtService.parseRefreshTokenUserId("refresh-token")).thenReturn(java.util.Optional.of(user.getId()));
        when(userRepository.findById(user.getId())).thenReturn(java.util.Optional.of(user));

        assertThatThrownBy(() -> authService.refresh("refresh-token"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}

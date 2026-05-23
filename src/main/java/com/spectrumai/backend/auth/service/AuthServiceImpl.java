package com.spectrumai.backend.auth.service;

import com.spectrumai.backend.audit.AuditAction;
import com.spectrumai.backend.audit.AuditService;
import com.spectrumai.backend.auth.dto.AuthResponse;
import com.spectrumai.backend.auth.dto.AuthenticatedUser;
import com.spectrumai.backend.auth.dto.LoginRequest;
import com.spectrumai.backend.auth.dto.RefreshResponse;
import com.spectrumai.backend.auth.dto.RegisterRequest;
import com.spectrumai.backend.auth.security.LoginAttemptService;
import com.spectrumai.backend.auth.security.UserPrincipal;
import com.spectrumai.backend.common.exception.BusinessException;
import com.spectrumai.backend.common.exception.ErrorCode;
import com.spectrumai.backend.common.util.RequestContext;
import com.spectrumai.backend.company.model.Company;
import com.spectrumai.backend.company.repository.CompanyRepository;
import com.spectrumai.backend.config.JwtProperties;
import com.spectrumai.backend.user.model.Role;
import com.spectrumai.backend.user.model.User;
import com.spectrumai.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;

    @Override
    public AuthResponse login(LoginRequest request) {
        String ip = RequestContext.clientIp();
        String email = request.email();

        if (loginAttemptService.isBlocked(email, ip)) {
            log.warn("AUTH_BLOCKED email={} ip={} reason=lockout", maskEmail(email), ip);
            auditService.record(AuditAction.LOGIN_BLOCKED, "user", null, "FAILURE",
                    Map.of("email", maskEmail(email), "ip", ip));
            throw new BusinessException(
                    "Muitas tentativas de login. Tente novamente em alguns minutos.",
                    HttpStatus.TOO_MANY_REQUESTS,
                    ErrorCode.RATE_LIMITED);
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !user.isActive()
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(email, ip);
            auditService.record(AuditAction.LOGIN_FAILURE, "user",
                    user == null ? null : user.getId().toString(), "FAILURE",
                    Map.of("email", maskEmail(email), "ip", ip));
            throw unauthorized();
        }

        loginAttemptService.recordSuccess(email, ip);
        log.info("AUTH_SUCCESS userId={} tenantId={} ip={}",
                user.getId(), user.getTenant().getId(), ip);
        auditService.recordSuccess(AuditAction.LOGIN_SUCCESS, "user", user.getId().toString());
        return buildAuthResponse(user);
    }

    private String maskEmail(String email) {
        if (email == null) return "?";
        int at = email.indexOf('@');
        if (at <= 2) return "***" + (at >= 0 ? email.substring(at) : "");
        return email.substring(0, 2) + "***" + email.substring(at);
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("E-mail já cadastrado", HttpStatus.CONFLICT, ErrorCode.VALIDATION_ERROR);
        }
        rejectPasswordContainingUserData(request.password(), request.email(), request.fullName(), request.companyName());

        Company tenant;

        if (!companyRepository.existsByName(request.companyName())) {
            tenant = companyRepository.save(Company.builder()
                    .id(UUID.randomUUID())
                    .name(request.companyName())
                    .active(true)
                    .build());
        } else {
            tenant = companyRepository.findByName(request.companyName());
        }

        User user = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .fullName(request.fullName())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.ADMIN)
                .active(true)
                .build());

        log.info("Cadastro: tenant={} ({}), user={} ({})",
                tenant.getName(), tenant.getId(), user.getEmail(), user.getId());
        auditService.record(AuditAction.USER_REGISTER, "user", user.getId().toString(), "SUCCESS",
                Map.of("role", user.getRole().name(), "tenantId", tenant.getId().toString()));
        return buildAuthResponse(user);
    }

    @Override
    public AuthResponse registerAnalyst(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("E-mail já cadastrado", HttpStatus.CONFLICT, ErrorCode.VALIDATION_ERROR);
        }
        rejectPasswordContainingUserData(request.password(), request.email(), request.fullName(), request.companyName());

        if (!companyRepository.existsByName(request.companyName())) {
            throw new BusinessException("Empresa não cadastrada", HttpStatus.NOT_FOUND, ErrorCode.VALIDATION_ERROR);
        }

        Company tenant = companyRepository.findByName(request.companyName());

        User user = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .fullName(request.fullName())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.ANALYST)
                .active(true)
                .build());

        log.info("Login: tenant={} ({}), user={} ({})",
                tenant.getName(), tenant.getId(), user.getEmail(), user.getId());
        auditService.record(AuditAction.USER_REGISTER, "user", user.getId().toString(), "SUCCESS",
                Map.of("role", user.getRole().name(), "tenantId", tenant.getId().toString()));
        return buildAuthResponse(user);
    }

    @Override
    public RefreshResponse refresh(String refreshToken) {
        UUID userId = jwtService.parseRefreshTokenUserId(refreshToken)
                .orElseThrow(this::unauthorized);

        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(this::unauthorized);

        String newAccessToken = jwtService.generateAccessToken(toPrincipal(user));
        return new RefreshResponse(newAccessToken, jwtProperties.expiresInSeconds());
    }

    private AuthResponse buildAuthResponse(User user) {
        UserPrincipal principal = toPrincipal(user);
        AuthenticatedUser authUser = new AuthenticatedUser(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getTenant().getId()
        );
        return new AuthResponse(
                jwtService.generateAccessToken(principal),
                jwtService.generateRefreshToken(principal),
                jwtProperties.expiresInSeconds(),
                authUser
        );
    }

    private UserPrincipal toPrincipal(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getTenant().getId(),
                user.getEmail(),
                user.getRole()
        );
    }

    private BusinessException unauthorized() {
        return new BusinessException("Credenciais inválidas", HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
    }

    /**
     * Rejeita senhas que contem trechos identificaveis do usuario (email local-part,
     * nome ou empresa). Bloqueia variantes triviais como senha = "JoaoSilva2024!".
     *
     * <p>Compara case-insensitive em janelas de 4 caracteres. Tokens com menos de
     * 4 chars sao ignorados (ex.: nome "Ana" nao bloqueia "Ana1234!").</p>
     */
    private void rejectPasswordContainingUserData(String password, String email, String fullName, String company) {
        if (password == null) return;
        String lowerPwd = password.toLowerCase();

        // Local-part do email (antes do @)
        String emailLocal = email == null ? "" : email.toLowerCase();
        int at = emailLocal.indexOf('@');
        if (at > 0) emailLocal = emailLocal.substring(0, at);

        String[] candidates = {
                emailLocal,
                fullName == null ? "" : fullName.toLowerCase(),
                company == null ? "" : company.toLowerCase()
        };

        for (String candidate : candidates) {
            if (candidate.length() < 4) continue;
            for (String token : candidate.split("[^a-z0-9]+")) {
                if (token.length() >= 4 && lowerPwd.contains(token)) {
                    throw new BusinessException(
                            "A senha não pode conter seu nome, e-mail ou nome da empresa.",
                            HttpStatus.BAD_REQUEST,
                            ErrorCode.VALIDATION_ERROR);
                }
            }
        }
    }
}

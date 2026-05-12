package com.spectrumai.backend.auth.service;

import com.spectrumai.backend.auth.dto.AuthResponse;
import com.spectrumai.backend.auth.dto.AuthenticatedUser;
import com.spectrumai.backend.auth.dto.LoginRequest;
import com.spectrumai.backend.auth.dto.RefreshResponse;
import com.spectrumai.backend.auth.dto.RegisterRequest;
import com.spectrumai.backend.auth.security.UserPrincipal;
import com.spectrumai.backend.common.exception.BusinessException;
import com.spectrumai.backend.common.exception.ErrorCode;
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

    @Override
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(this::unauthorized);

        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw unauthorized();
        }

        log.info("Login bem-sucedido: userId={}, tenantId={}", user.getId(), user.getTenant().getId());
        return buildAuthResponse(user);
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("E-mail já cadastrado", HttpStatus.CONFLICT, ErrorCode.VALIDATION_ERROR);
        }

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
        return buildAuthResponse(user);
    }

    @Override
    public AuthResponse registerAnalyst(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("E-mail já cadastrado", HttpStatus.CONFLICT, ErrorCode.VALIDATION_ERROR);
        }

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
}

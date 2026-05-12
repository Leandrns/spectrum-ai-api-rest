package com.spectrumai.backend.auth.controller;

import com.spectrumai.backend.auth.dto.AuthResponse;
import com.spectrumai.backend.auth.dto.LoginRequest;
import com.spectrumai.backend.auth.dto.RefreshRequest;
import com.spectrumai.backend.auth.dto.RefreshResponse;
import com.spectrumai.backend.auth.dto.RegisterRequest;
import com.spectrumai.backend.auth.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Autenticação e cadastro")
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/registerAnalyst")
    public AuthResponse registerAnalyst(@Valid @RequestBody RegisterRequest request) {
        return authService.registerAnalyst(request);
    }

    @PostMapping("/refresh")
    public RefreshResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }
}

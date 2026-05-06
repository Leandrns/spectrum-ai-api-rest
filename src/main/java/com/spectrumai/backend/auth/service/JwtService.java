package com.spectrumai.backend.auth.service;

import org.springframework.security.core.Authentication;

import java.util.Optional;

public interface JwtService {

    String generateAccessToken(Authentication authentication);

    String generateRefreshToken(Authentication authentication);

    Optional<Authentication> parseAuthentication(String token);
}

package com.spectrumai.backend.auth.service;

import com.spectrumai.backend.auth.dto.AuthResponse;
import com.spectrumai.backend.auth.dto.LoginRequest;
import com.spectrumai.backend.auth.dto.RefreshResponse;
import com.spectrumai.backend.auth.dto.RegisterRequest;

public interface AuthService {

    AuthResponse login(LoginRequest request);

    AuthResponse register(RegisterRequest request);

    RefreshResponse refresh(String refreshToken);
}

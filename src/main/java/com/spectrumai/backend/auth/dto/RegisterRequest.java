package com.spectrumai.backend.auth.dto;

import com.spectrumai.backend.auth.password.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 120) String companyName,
        @NotBlank @Size(max = 120) String fullName,
        @Email @NotBlank @Size(max = 254) String email,
        @NotBlank @StrongPassword String password
) {}

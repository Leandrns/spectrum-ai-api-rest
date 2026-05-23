package com.spectrumai.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 120) String companyName,
        @NotBlank @Size(max = 120) String fullName,
        @Email @NotBlank @Size(max = 254) String email,
        @NotBlank
        @Size(min = 8, max = 128)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "senha deve conter ao menos 1 letra maiuscula, 1 minuscula e 1 digito"
        )
        String password
) {}

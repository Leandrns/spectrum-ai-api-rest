package com.spectrumai.backend.auth.security;

import com.spectrumai.backend.user.model.Role;

import java.util.UUID;

public record UserPrincipal(
        UUID userId,
        UUID tenantId,
        String email,
        Role role
) {}

package com.spectrumai.backend.auth.dto;

import com.spectrumai.backend.user.model.Role;

import java.util.UUID;

/** Representação do usuário autenticado retornada nas respostas de auth. */
public record AuthenticatedUser(
        UUID id,
        String name,
        String email,
        Role role,
        UUID tenantId
) {}

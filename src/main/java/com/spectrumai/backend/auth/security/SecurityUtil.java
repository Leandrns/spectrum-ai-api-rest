package com.spectrumai.backend.auth.security;

import com.spectrumai.backend.common.exception.BusinessException;
import com.spectrumai.backend.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/** Acesso ao {@link UserPrincipal} ativo no {@link SecurityContextHolder}. */
public final class SecurityUtil {

    private SecurityUtil() {}

    public static UserPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new BusinessException("Usuário não autenticado", HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }

    public static UUID currentUserId() {
        return currentPrincipal().userId();
    }
}

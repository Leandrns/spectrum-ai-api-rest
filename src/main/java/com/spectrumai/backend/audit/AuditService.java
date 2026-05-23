package com.spectrumai.backend.audit;

import com.spectrumai.backend.auth.security.UserPrincipal;
import com.spectrumai.backend.common.util.RequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Persiste eventos de auditoria de forma assincrona. Falhas na escrita
 * de auditoria nunca podem quebrar a operacao de negocio que a originou.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    @Async
    public void record(String action, String resourceType, String resourceId, String outcome,
                       Map<String, Object> details) {
        try {
            UserPrincipal principal = currentPrincipal();
            AuditLog entry = AuditLog.builder()
                    .id(UUID.randomUUID())
                    .tenantId(principal == null ? null : principal.tenantId())
                    .actorId(principal == null ? null : principal.userId())
                    .actorEmail(principal == null ? null : principal.email())
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .ipAddress(RequestContext.clientIp())
                    .userAgent(truncate(RequestContext.userAgent(), 512))
                    .details(details)
                    .outcome(outcome == null ? "SUCCESS" : outcome)
                    .build();
            repository.save(entry);
        } catch (Exception ex) {
            // Nao propagar: auditoria nao pode quebrar o fluxo de negocio
            log.error("Falha ao gravar audit_log action={} resource={}/{}", action, resourceType, resourceId, ex);
        }
    }

    public void recordSuccess(String action, String resourceType, String resourceId) {
        record(action, resourceType, resourceId, "SUCCESS", null);
    }

    public void recordSuccess(String action, String resourceType, String resourceId, Map<String, Object> details) {
        record(action, resourceType, resourceId, "SUCCESS", details);
    }

    public void recordFailure(String action, String resourceType, String resourceId, Map<String, Object> details) {
        record(action, resourceType, resourceId, "FAILURE", details);
    }

    private UserPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal p) {
            return p;
        }
        return null;
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}

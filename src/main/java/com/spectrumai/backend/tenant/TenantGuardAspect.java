package com.spectrumai.backend.tenant;

import com.spectrumai.backend.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Camada de defesa contra vazamento entre tenants: intercepta {@code findById}
 * em qualquer {@link org.springframework.data.repository.CrudRepository} cujo
 * resultado seja {@link TenantAware} e oculta a entidade caso o tenant não
 * corresponda ao {@link TenantContext} atual.
 *
 * <p>Quando {@link TenantContext#getTenantId()} é {@code null} (endpoints
 * públicos, jobs internos, testes) o aspecto não interfere.</p>
 */
@Slf4j
@Aspect
@Component
public class TenantGuardAspect {

    @Around("execution(* org.springframework.data.repository.CrudRepository+.findById(..))")
    public Object guardFindById(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null || !(result instanceof Optional<?> opt) || opt.isEmpty()) {
            return result;
        }
        Object entity = opt.get();
        if (entity instanceof TenantAware aware && !tenantId.equals(aware.getTenantId())) {
            log.warn("Tentativa de acesso cross-tenant bloqueada: tenant={} entity={} type={}",
                    tenantId, aware.getTenantId(), entity.getClass().getSimpleName());
            throw new ResourceNotFoundException("Recurso não encontrado");
        }
        return result;
    }
}

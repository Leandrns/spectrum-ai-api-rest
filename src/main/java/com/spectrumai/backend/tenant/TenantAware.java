package com.spectrumai.backend.tenant;

import java.util.UUID;

/**
 * Marca entidades de domínio escopo-tenant. Usado pelo {@link TenantGuardAspect}
 * para validar acesso cruzado entre tenants em chamadas {@code find*ById}.
 */
public interface TenantAware {

    UUID getTenantId();
}

package com.spectrumai.backend.session.service;

import com.spectrumai.backend.auth.security.SecurityUtil;
import com.spectrumai.backend.common.exception.ResourceNotFoundException;
import com.spectrumai.backend.company.model.Company;
import com.spectrumai.backend.company.repository.CompanyRepository;
import com.spectrumai.backend.session.model.AnalysisSession;
import com.spectrumai.backend.session.repository.AnalysisSessionRepository;
import com.spectrumai.backend.tenant.TenantContext;
import com.spectrumai.backend.user.model.User;
import com.spectrumai.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SessionServiceImpl implements SessionService {

    private final AnalysisSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;

    @Override
    public AnalysisSession create(String name, String description) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = SecurityUtil.currentUserId();
        Company tenant = companyRepository.getReferenceById(tenantId);
        User owner = userRepository.getReferenceById(userId);

        AnalysisSession session = AnalysisSession.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .owner(owner)
                .name(name)
                .description(description)
                .active(true)
                .build();

        AnalysisSession saved = sessionRepository.save(session);
        log.info("Sessão criada: id={} tenant={} owner={}", saved.getId(), tenantId, userId);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public AnalysisSession getById(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        AnalysisSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada: " + id));
        if (!tenantId.equals(session.getTenant().getId())) {
            throw new ResourceNotFoundException("Sessão não encontrada: " + id);
        }
        return session;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AnalysisSession> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();
        return sessionRepository.findByTenant_IdOrderByCreatedAtDesc(tenantId, pageable);
    }
}

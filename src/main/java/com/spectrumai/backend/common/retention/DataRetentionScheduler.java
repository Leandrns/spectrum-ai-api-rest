package com.spectrumai.backend.common.retention;

import com.spectrumai.backend.audit.AuditAction;
import com.spectrumai.backend.audit.AuditLogRepository;
import com.spectrumai.backend.audit.AuditService;
import com.spectrumai.backend.config.SecurityProperties;
import com.spectrumai.backend.search.repository.SearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Job diario de descarte seguro de dados antigos (LGPD/GDPR). Configurado via
 * {@code spectrum.security.retention.*}. Executa apenas se
 * {@code retention.enabled=true}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataRetentionScheduler {

    private final SecurityProperties securityProperties;
    private final SearchRepository searchRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;

    @Scheduled(cron = "${spectrum.security.retention.cron:0 0 3 * * *}")
    @Transactional
    public void purgeOldData() {
        if (!securityProperties.retention().enabled()) {
            log.debug("Retention scheduler desabilitado");
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> outcome = new LinkedHashMap<>();

        int searches = searchRepository.deleteCompletedBefore(
                now.minusDays(securityProperties.retention().searchesDays()));
        outcome.put("searches", searches);

        int audits = auditLogRepository.deleteOlderThan(
                now.minusDays(securityProperties.retention().auditDays()));
        outcome.put("auditLogs", audits);

        log.info("DATA_PURGED searches={} auditLogs={}", searches, audits);
        auditService.recordSuccess(AuditAction.DATA_PURGED, "system", null, outcome);
    }
}

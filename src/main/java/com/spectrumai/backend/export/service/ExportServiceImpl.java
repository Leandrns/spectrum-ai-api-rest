package com.spectrumai.backend.export.service;

import com.spectrumai.backend.audit.AuditAction;
import com.spectrumai.backend.audit.AuditService;
import com.spectrumai.backend.auth.security.SecurityUtil;
import com.spectrumai.backend.common.exception.BusinessException;
import com.spectrumai.backend.common.exception.ErrorCode;
import com.spectrumai.backend.config.AppProperties;
import com.spectrumai.backend.export.ExportFormat;
import com.spectrumai.backend.export.ExportScope;
import com.spectrumai.backend.export.dto.VehicleSpecRow;
import com.spectrumai.backend.export.model.DataExport;
import com.spectrumai.backend.export.repository.DataExportRepository;
import com.spectrumai.backend.export.spec.SpecsFlattener;
import com.spectrumai.backend.export.storage.ExportStorage;
import com.spectrumai.backend.export.writer.ExportWriter;
import com.spectrumai.backend.export.writer.ExportWriterResolver;
import com.spectrumai.backend.search.dto.SearchExportResponse;
import com.spectrumai.backend.search.model.Search;
import com.spectrumai.backend.search.model.SearchStatus;
import com.spectrumai.backend.search.repository.SearchRepository;
import com.spectrumai.backend.session.model.AnalysisSession;
import com.spectrumai.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private static final Duration DEFAULT_URL_TTL = Duration.ofMinutes(60);
    private static final String DEFAULT_OBJECT_PREFIX = "exports";

    /** Prefixo do SHA-256 usado no nome do objeto — 12 hex tornam colisão irrelevante aqui. */
    private static final int HASH_NAME_LENGTH = 12;

    private final SearchRepository searchRepository;
    private final DataExportRepository exportRepository;
    private final SpecsFlattener flattener;
    private final ExportWriterResolver writerResolver;
    private final ExportStorage storage;
    private final AuditService auditService;
    private final AppProperties properties;

    @Override
    public SearchExportResponse exportSearch(Search search, ExportFormat format) {
        if (search.getStatus() != SearchStatus.COMPLETED) {
            throw new BusinessException(
                    "A pesquisa ainda não foi concluída (status " + search.getStatus() + ").",
                    HttpStatus.CONFLICT,
                    ErrorCode.VALIDATION_ERROR);
        }

        List<VehicleSpecRow> rows = flattener.flatten(search);
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "A pesquisa não possui especificações para exportar.",
                    HttpStatus.CONFLICT,
                    ErrorCode.VALIDATION_ERROR);
        }

        String filename = "spectrum_" + slug(search.getBrand() + " " + search.getModel() + " "
                + (search.getYear() == null ? "" : search.getYear().toString()));

        return generate(ExportScope.SEARCH, search.getId(), format, rows, filename,
                AuditAction.SEARCH_EXPORTED, "search");
    }

    @Override
    public SearchExportResponse exportSession(AnalysisSession session, ExportFormat format) {
        List<Search> searches = searchRepository
                .findByTenant_IdAndSession_IdAndStatusOrderByCompletedAtDesc(
                        session.getTenantId(), session.getId(), SearchStatus.COMPLETED);

        List<VehicleSpecRow> rows = new ArrayList<>();
        for (Search search : dedupeByVehicle(searches)) {
            rows.addAll(flattener.flatten(search));
        }
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "A sessão não possui pesquisas concluídas para exportar.",
                    HttpStatus.CONFLICT,
                    ErrorCode.VALIDATION_ERROR);
        }

        String filename = "spectrum_sessao_" + slug(session.getName());

        return generate(ExportScope.SESSION, session.getId(), format, rows, filename,
                AuditAction.SESSION_EXPORTED, "session");
    }

    /**
     * Gera o arquivo, reaproveita o objeto no bucket quando o conteúdo não mudou e
     * devolve uma URL de download temporária.
     *
     * <p>O arquivo é sempre gerado: montá-lo a partir do banco custa pouco (algumas
     * centenas de linhas) e o hash do resultado é o que decide se vale reenviar. Isso
     * dispensa qualquer heurística de invalidação — uma sessão que ganhou pesquisas
     * novas produz um hash diferente e sobe um objeto novo sozinha.
     */
    private SearchExportResponse generate(ExportScope scope, UUID resourceId, ExportFormat format,
                                          List<VehicleSpecRow> rows, String filename,
                                          String auditAction, String auditResourceType) {
        UUID tenantId = TenantContext.requireTenantId();
        ExportWriter writer = writerResolver.resolve(format);

        byte[] content = writer.write(rows);
        String hash = sha256(content);
        String downloadFilename = filename + "." + format.extension();

        Optional<DataExport> cached = exportRepository
                .findByTenantIdAndScopeAndResourceIdAndFormat(tenantId, scope, resourceId, format);

        boolean reused = cached.isPresent()
                && hash.equals(cached.get().getContentHash())
                && storage.exists(cached.get().getObjectName());

        String objectName;
        if (reused) {
            objectName = cached.get().getObjectName();
            log.debug("Export reaproveitado: scope={} resource={} object={}", scope, resourceId, objectName);
        } else {
            objectName = buildObjectName(tenantId, scope, resourceId, hash, format);
            storage.upload(objectName, content, format.contentType(), downloadFilename);
            persist(cached.orElse(null), tenantId, scope, resourceId, format, objectName, hash,
                    content.length, rows.size());
        }

        Duration ttl = urlTtl();
        String downloadUrl = storage.signedUrl(objectName, ttl);
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(ttl);

        auditService.recordSuccess(auditAction, auditResourceType, resourceId.toString(),
                Map.of("format", format.name(), "rows", rows.size(), "cached", reused));

        return new SearchExportResponse(downloadUrl, expiresAt);
    }

    private void persist(DataExport existing, UUID tenantId, ExportScope scope, UUID resourceId,
                         ExportFormat format, String objectName, String hash,
                         int sizeBytes, int rowCount) {
        DataExport entry = existing == null
                ? DataExport.builder().id(UUID.randomUUID()).createdBy(SecurityUtil.currentUserId()).build()
                : existing;
        entry.setTenantId(tenantId);
        entry.setScope(scope);
        entry.setResourceId(resourceId);
        entry.setFormat(format);
        entry.setObjectName(objectName);
        entry.setContentHash(hash);
        entry.setSizeBytes(sizeBytes);
        entry.setRowCount(rowCount);
        exportRepository.save(entry);
    }

    /**
     * Uma sessão pode ter o mesmo veículo pesquisado mais de uma vez. Como o arquivo
     * não carrega {@code search_id}, linhas repetidas ficariam indistinguíveis no BI —
     * então vale a pesquisa mais recente de cada veículo.
     */
    private List<Search> dedupeByVehicle(List<Search> searches) {
        // A query já vem ordenada por completed_at desc: o primeiro de cada chave vence.
        Map<String, Search> latestByVehicle = new LinkedHashMap<>();
        for (Search search : searches) {
            latestByVehicle.putIfAbsent(vehicleKey(search), search);
        }
        return latestByVehicle.values().stream()
                .sorted(Comparator
                        .comparing((Search s) -> nullToEmpty(s.getBrand()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(s -> nullToEmpty(s.getModel()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(s -> nullToEmpty(s.getTrim()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(s -> s.getYear() == null ? Short.MIN_VALUE : s.getYear()))
                .toList();
    }

    private String vehicleKey(Search s) {
        return String.join("|",
                nullToEmpty(s.getBrand()).toLowerCase(Locale.ROOT),
                nullToEmpty(s.getModel()).toLowerCase(Locale.ROOT),
                nullToEmpty(s.getTrim()).toLowerCase(Locale.ROOT),
                s.getYear() == null ? "" : s.getYear().toString());
    }

    private String buildObjectName(UUID tenantId, ExportScope scope, UUID resourceId,
                                   String hash, ExportFormat format) {
        return String.join("/",
                objectPrefix(),
                tenantId.toString(),
                scope.name().toLowerCase(Locale.ROOT),
                resourceId.toString(),
                hash.substring(0, HASH_NAME_LENGTH) + "." + format.extension());
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }

    /** Normaliza para um nome de arquivo seguro: sem acentos, espaços ou pontuação. */
    private String slug(String value) {
        if (value == null || value.isBlank()) {
            return "export";
        }
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return slug.isBlank() ? "export" : slug;
    }

    private Duration urlTtl() {
        AppProperties.Storage.Gcs gcs = gcsOrNull();
        if (gcs == null || gcs.signedUrlTtlMinutes() <= 0) {
            return DEFAULT_URL_TTL;
        }
        return Duration.ofMinutes(gcs.signedUrlTtlMinutes());
    }

    private String objectPrefix() {
        AppProperties.Storage.Gcs gcs = gcsOrNull();
        if (gcs == null || gcs.objectPrefix() == null || gcs.objectPrefix().isBlank()) {
            return DEFAULT_OBJECT_PREFIX;
        }
        return gcs.objectPrefix().replaceAll("^/+|/+$", "");
    }

    private AppProperties.Storage.Gcs gcsOrNull() {
        return properties.storage() == null ? null : properties.storage().gcs();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}

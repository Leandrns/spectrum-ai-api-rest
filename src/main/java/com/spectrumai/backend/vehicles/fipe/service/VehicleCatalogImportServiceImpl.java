package com.spectrumai.backend.vehicles.fipe.service;

import com.spectrumai.backend.common.exception.BusinessException;
import com.spectrumai.backend.common.exception.ErrorCode;
import com.spectrumai.backend.config.AppProperties;
import com.spectrumai.backend.vehicles.fipe.client.FipeApiClient;
import com.spectrumai.backend.vehicles.fipe.client.FipeApiException;
import com.spectrumai.backend.vehicles.fipe.dto.FipeBrand;
import com.spectrumai.backend.vehicles.fipe.dto.FipeModel;
import com.spectrumai.backend.vehicles.fipe.dto.FipeYear;
import com.spectrumai.backend.vehicles.fipe.dto.VehicleImportReport;
import com.spectrumai.backend.vehicles.fipe.filter.VehicleSegmentFilter;
import com.spectrumai.backend.vehicles.model.VehicleCatalogEntry;
import com.spectrumai.backend.vehicles.repository.VehicleCatalogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Importa o catálogo de veículos a partir da API FIPE. Usado exclusivamente
 * para popular {@code vehicles_catalog}; o uso da FIPE em runtime (consultas
 * dos usuários) permanece a partir do banco local.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleCatalogImportServiceImpl implements VehicleCatalogImportService {

    /** "Zero KM" na FIPE vem como código "32000-N"; mapeia para o ano vigente. */
    private static final int FIPE_ZERO_KM_YEAR_CODE = 32000;

    /** Tamanho da janela para flush incremental no banco. */
    private static final int BATCH_SIZE = 200;

    private static final Pattern YEAR_PREFIX = Pattern.compile("^(\\d{4})");

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);

    private final FipeApiClient fipeClient;
    private final VehicleCatalogRepository repository;
    private final AppProperties properties;
    private final VehicleSegmentFilter segmentFilter;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public VehicleImportReport importFromFipe() {
        if (!fipeClient.isConfigured()) {
            throw new BusinessException(
                    "Token da FIPE não configurado. Defina FIPE_API_TOKEN antes de iniciar o import.",
                    HttpStatus.PRECONDITION_FAILED,
                    ErrorCode.VALIDATION_ERROR);
        }
        if (!running.compareAndSet(false, true)) {
            throw new BusinessException(
                    "Import já em andamento.",
                    HttpStatus.CONFLICT,
                    ErrorCode.VALIDATION_ERROR);
        }
        cancelRequested.set(false);

        Instant start = Instant.now();
        log.info("Iniciando import FIPE -> vehicles_catalog");

        try {
            repository.deleteAllInBatch();

            List<FipeBrand> brands = fipeClient.listBrands();
            log.info("FIPE retornou {} marcas", brands.size());

            List<VehicleCatalogEntry> buffer = new ArrayList<>(BATCH_SIZE);
            int modelsProcessed = 0;
            int inserted = 0;
            int failures = 0;
            int fallbackYear = currentYearFallback();

            for (FipeBrand brand : brands) {
                if (cancelRequested.get()) {
                    log.warn("Import FIPE cancelado após {} marcas processadas. Rollback será aplicado.", modelsProcessed);
                    throw new ImportCancelledException();
                }

                List<FipeModel> models;
                try {
                    models = fipeClient.listModels(brand.code());
                } catch (FipeApiException e) {
                    log.warn("Falha ao listar modelos da marca {} ({}): {}", brand.name(), brand.code(), e.getMessage());
                    failures++;
                    throttle();
                    continue;
                }
                throttle();

                for (FipeModel model : models) {
                    if (properties.fipe().filterSegments() && !segmentFilter.isIncluded(model.name())) {
                        continue;
                    }
                    modelsProcessed++;
                    List<FipeYear> years;
                    try {
                        years = fipeClient.listYears(brand.code(), model.code());
                    } catch (FipeApiException e) {
                        log.warn("Falha ao listar anos do modelo {}/{}: {}", brand.name(), model.name(), e.getMessage());
                        failures++;
                        throttle();
                        continue;
                    }
                    throttle();

                    int[] range = extractYearRange(years, fallbackYear);
                    if (range == null) {
                        continue;
                    }

                    buffer.add(toEntry(brand.name(), model.name(), range[0], range[1]));
                    if (buffer.size() >= BATCH_SIZE) {
                        inserted += flush(buffer);
                    }
                }
                log.info("Marca '{}' processada ({} modelos acumulados)", brand.name(), modelsProcessed);
            }

            if (!buffer.isEmpty()) {
                inserted += flush(buffer);
            }

            Duration elapsed = Duration.between(start, Instant.now());
            log.info("Import FIPE concluído: {} marcas, {} modelos, {} entradas inseridas, {} falhas em {}s",
                    brands.size(), modelsProcessed, inserted, failures, elapsed.toSeconds());
            return VehicleImportReport.of(brands.size(), modelsProcessed, inserted, failures, elapsed);
        } finally {
            running.set(false);
            cancelRequested.set(false);
        }
    }

    @Override
    public void cancel() {
        if (!running.get()) {
            throw new BusinessException(
                    "Nenhum import em andamento.",
                    HttpStatus.CONFLICT,
                    ErrorCode.VALIDATION_ERROR);
        }
        cancelRequested.set(true);
        log.info("Cancelamento do import FIPE solicitado.");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Faz persist direto via EntityManager (mais rápido que {@code saveAll}, pois
     * evita o SELECT que o Hibernate dispara em entidades com {@code @Id}
     * atribuído manualmente). O {@code flush + clear} mantém a sessão enxuta.
     */
    private int flush(List<VehicleCatalogEntry> buffer) {
        int size = buffer.size();
        for (VehicleCatalogEntry entry : buffer) {
            entityManager.persist(entry);
        }
        entityManager.flush();
        entityManager.clear();
        buffer.clear();
        return size;
    }

    private VehicleCatalogEntry toEntry(String brand, String modelName, int yearFrom, int yearTo) {
        return VehicleCatalogEntry.builder()
                .id(UUID.randomUUID())
                .brand(truncate(brand, 100))
                .model(truncate(modelName, 100))
                .trim(null)
                .yearFrom((short) yearFrom)
                .yearTo((short) yearTo)
                .build();
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * Reduz a lista de {@link FipeYear} a [min, max]. O FIPE devolve "Zero KM"
     * com código 32000-N e nome contendo a string "Zero KM" — quando isso ocorre
     * usamos o ano corrente como teto.
     */
    private static int[] extractYearRange(List<FipeYear> years, int fallback) {
        if (years == null || years.isEmpty()) return null;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (FipeYear y : years) {
            Integer parsed = parseYear(y, fallback);
            if (parsed == null) continue;
            if (parsed < min) min = parsed;
            if (parsed > max) max = parsed;
        }
        if (min == Integer.MAX_VALUE) return null;
        if (min > max) min = max;
        // Banco usa SMALLINT (year_from/year_to) — limite por segurança.
        min = Math.max(1900, Math.min(min, 9999));
        max = Math.max(1900, Math.min(max, 9999));
        return new int[]{min, max};
    }

    private static Integer parseYear(FipeYear year, int fallback) {
        if (year == null) return null;
        String code = year.code();
        if (code != null) {
            int dash = code.indexOf('-');
            String prefix = dash >= 0 ? code.substring(0, dash) : code;
            try {
                int value = Integer.parseInt(prefix);
                if (value == FIPE_ZERO_KM_YEAR_CODE) {
                    return fallback;
                }
                return value;
            } catch (NumberFormatException ignored) {
                // tenta pelo nome abaixo
            }
        }
        String name = year.name();
        if (name == null) return null;
        Matcher m = YEAR_PREFIX.matcher(name);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

    private int currentYearFallback() {
        return java.time.Year.now().getValue();
    }

    private void throttle() {
        int delay = properties.fipe().requestDelayMs();
        if (delay <= 0) return;
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

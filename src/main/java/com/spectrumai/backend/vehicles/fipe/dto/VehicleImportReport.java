package com.spectrumai.backend.vehicles.fipe.dto;

import java.time.Duration;

/** Resumo retornado ao final do import do catálogo a partir da FIPE. */
public record VehicleImportReport(
        int brandsProcessed,
        int modelsProcessed,
        int entriesInserted,
        int failures,
        long durationSeconds
) {
    public static VehicleImportReport of(int brands, int models, int inserted, int failures, Duration duration) {
        return new VehicleImportReport(brands, models, inserted, failures, duration.toSeconds());
    }
}

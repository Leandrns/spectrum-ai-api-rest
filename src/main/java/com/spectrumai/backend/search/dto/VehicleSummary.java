package com.spectrumai.backend.search.dto;

public record VehicleSummary(
        String brand,
        String model,
        String trim,
        Integer year
) {}

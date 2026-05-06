package com.spectrumai.backend.vehicles.dto;

import java.util.List;

public record ModelInfo(
        String name,
        List<Integer> years
) {}

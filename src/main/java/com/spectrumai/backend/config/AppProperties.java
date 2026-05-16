package com.spectrumai.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spectrum")
public record AppProperties(Ai ai, Fipe fipe) {

    public record Ai(String provider, Gemini gemini) {
        public record Gemini(String apiKey, String model, boolean groundingEnabled, int timeoutSeconds) {}
    }

    /**
     * Configuração da API FIPE (https://fipe.online/docs/api/fipe), utilizada
     * exclusivamente para popular o catálogo de veículos.
     */
    public record Fipe(String baseUrl, String apiToken, String vehicleType, int timeoutSeconds, int requestDelayMs, boolean filterSegments, int minYear) {}
}

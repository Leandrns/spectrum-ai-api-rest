package com.spectrumai.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spectrum")
public record AppProperties(Ai ai) {

    public record Ai(String provider, Gemini gemini) {
        public record Gemini(String apiKey, String model, boolean groundingEnabled, int timeoutSeconds) {}
    }
}

package com.spectrumai.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "spectrum.security")
public record SecurityProperties(
        Cors cors,
        boolean requireHttps,
        RateLimit rateLimit,
        Lockout lockout,
        Encryption encryption,
        Retention retention
) {

    public record Cors(
            List<String> allowedOrigins,
            List<String> allowedMethods,
            List<String> allowedHeaders,
            List<String> exposedHeaders,
            boolean allowCredentials,
            long maxAge
    ) {}

    public record RateLimit(
            boolean enabled,
            int defaultPerMinute,
            int authPerMinute
    ) {}

    public record Lockout(
            boolean enabled,
            int maxFailures,
            int windowSeconds
    ) {}

    public record Encryption(String aesKey) {}

    public record Retention(
            boolean enabled,
            int searchesDays,
            int sessionsDays,
            int auditDays,
            String cron
    ) {}
}

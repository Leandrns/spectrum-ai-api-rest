package com.spectrumai.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS restritivo: lista de origens, m�todos e headers vem de
 * {@link SecurityProperties} (env vars). N�o permite wildcard quando
 * {@code allowCredentials=true} (combina��o insegura).
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    private final SecurityProperties securityProperties;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        SecurityProperties.Cors props = securityProperties.cors();
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = props.allowedOrigins();
        boolean hasWildcard = origins != null && origins.contains("*");

        if (hasWildcard && props.allowCredentials()) {
            throw new IllegalStateException(
                    "CORS inseguro: allowedOrigins=\"*\" n�o pode ser combinado com allowCredentials=true. "
                            + "Defina CORS_ALLOWED_ORIGINS com dom�nios espec�ficos.");
        }

        if (hasWildcard) {
            config.setAllowedOriginPatterns(origins);
        } else {
            config.setAllowedOrigins(origins);
        }

        config.setAllowedMethods(props.allowedMethods());
        config.setAllowedHeaders(props.allowedHeaders());
        config.setExposedHeaders(props.exposedHeaders());
        config.setAllowCredentials(props.allowCredentials());
        config.setMaxAge(props.maxAge());

        log.info("CORS configurado: origins={}, credentials={}", origins, props.allowCredentials());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/v1/**", config);
        return source;
    }
}

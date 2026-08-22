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
 * CORS restritivo: lista de origens, metodos e headers vem de
 * {@link SecurityProperties} (env vars). Nao permite wildcard quando
 * {@code allowCredentials=true} (combinacao insegura).
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

        // Uma lista vazia bloqueia 100% das requisicoes com "Invalid CORS request",
        // e o sintoma (403 no preflight) nao aponta para a causa. Isso acontece
        // quando o docker-compose passa CORS_ALLOWED_ORIGINS como string vazia:
        // o default do Spring (${VAR:default}) so vale se a variavel NAO existir.
        if (origins == null || origins.isEmpty()) {
            throw new IllegalStateException(
                    "CORS sem origens configuradas: nenhum frontend conseguiria chamar a API. "
                            + "Defina CORS_ALLOWED_ORIGINS (no .env ou no ambiente). Atencao: passar "
                            + "a variavel VAZIA nao cai no default — o valor vazio vence.");
        }

        boolean allowsAnyOrigin = origins.contains("*");
        if (allowsAnyOrigin && props.allowCredentials()) {
            throw new IllegalStateException(
                    "CORS inseguro: allowedOrigins=\"*\" nao pode ser combinado com allowCredentials=true. "
                            + "Defina CORS_ALLOWED_ORIGINS com dominios especificos.");
        }

        // Padroes com curinga (ex.: http://192.168.*.*:*) sao necessarios em dev:
        // o Expo serve em porta variavel e o celular acessa pelo IP da maquina na
        // rede, entao nao ha como enumerar as origens. setAllowedOriginPatterns
        // aceita curinga junto com credentials — setAllowedOrigins nao.
        boolean hasPattern = origins.stream().anyMatch(o -> o.contains("*"));
        if (hasPattern) {
            if (!allowsAnyOrigin) {
                log.warn("CORS usando padroes com curinga: {}. Adequado para desenvolvimento; "
                        + "em producao prefira origens exatas.", origins);
            }
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

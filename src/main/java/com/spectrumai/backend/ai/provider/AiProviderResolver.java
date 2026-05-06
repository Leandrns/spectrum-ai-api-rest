package com.spectrumai.backend.ai.provider;

import com.spectrumai.backend.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AiProviderResolver {

    private final AppProperties properties;
    private final List<AiProvider> providers;

    public AiProviderResolver(AppProperties properties, List<AiProvider> providers) {
        this.properties = properties;
        this.providers = providers;
    }

    public AiProvider resolveDefault() {
        String configured = properties.ai().provider();
        return providers.stream()
                .filter(p -> p.name().equalsIgnoreCase(configured))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Provider de IA não encontrado: " + configured));
    }

    public AiProvider resolveByName(String name) {
        return providers.stream()
                .filter(p -> p.name().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Provider de IA não encontrado: " + name));
    }
}

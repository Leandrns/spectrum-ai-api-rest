package com.spectrumai.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, AppProperties.class, SecurityProperties.class})
public class PropertiesConfig {
}

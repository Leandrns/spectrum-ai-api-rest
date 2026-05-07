package com.spectrumai.backend.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Configuração manual do Flyway para Spring Boot 4.0.
 */
@Configuration
public class FlywayManualConfig {

    /**
     * Bean do Flyway configurado a partir do {@link DataSource} já criado pelo
     * Spring Boot. {@code initMethod = "migrate"} faz o Flyway rodar a migração
     * durante a inicialização do bean.
     */
    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
    }

    /**
     * Adiciona a dependência {@code "flyway"} em todo bean do tipo
     * {@code EntityManagerFactory}. Sem isso, o Hibernate poderia validar o
     * schema antes de o Flyway rodar a migração, o que causaria erro
     * "Schema validation: missing table".
     *
     * <p>O método é {@code static} porque {@link BeanFactoryPostProcessor}
     * precisa estar disponível antes da fase normal de criação de beans.</p>
     */
    @Bean
    public static BeanFactoryPostProcessor entityManagerFactoryDependsOnFlyway() {
        return beanFactory -> {
            String[] emfBeanNames = beanFactory.getBeanNamesForType(
                    jakarta.persistence.EntityManagerFactory.class, true, false);
            for (String beanName : emfBeanNames) {
                BeanDefinition bd = beanFactory.getBeanDefinition(beanName);
                Set<String> dependsOn = new LinkedHashSet<>();
                if (bd.getDependsOn() != null) {
                    for (String dep : bd.getDependsOn()) {
                        dependsOn.add(dep);
                    }
                }
                dependsOn.add("flyway");
                bd.setDependsOn(dependsOn.toArray(new String[0]));
            }
        };
    }
}

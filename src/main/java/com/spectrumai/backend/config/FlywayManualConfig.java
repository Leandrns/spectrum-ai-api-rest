package com.spectrumai.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Configuracao manual do Flyway para Spring Boot 4.0.
 */
@Slf4j
@Configuration
public class FlywayManualConfig {

    /**
     * Bean do Flyway configurado a partir do {@link DataSource} ja criado pelo
     * Spring Boot. Sem {@code initMethod} aqui: a migracao e disparada pelo
     * bean {@link #flywayInitializer} para permitir um {@code repair()}
     * opcional antes do {@code migrate()}.
     */
    @Bean
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .cleanDisabled(true)
                .load();
    }

    /**
     * Dispara {@code migrate()} (e opcionalmente {@code repair()} antes) durante
     * a inicializacao da aplicacao.
     *
     * <p>{@code repair()} reescreve os checksums armazenados em
     * {@code flyway_schema_history} com os checksums dos arquivos locais. Isso
     * resolve mismatch quando uma migration ja aplicada teve seus bytes
     * alterados (ex.: re-encoding UTF-8 de comentarios). Use sob demanda -
     * mantenha {@code spectrum.flyway.repair-on-start=false} em operacao
     * normal para preservar a deteccao de alteracoes acidentais.</p>
     */
    @Bean(initMethod = "run")
    public FlywayInitializer flywayInitializer(
            Flyway flyway,
            @Value("${spectrum.flyway.repair-on-start:false}") boolean repairOnStart
    ) {
        return new FlywayInitializer(flyway, repairOnStart);
    }

    /**
     * Adiciona a dependencia {@code "flywayInitializer"} em todo bean do tipo
     * {@code EntityManagerFactory}. Sem isso, o Hibernate poderia validar o
     * schema antes de o Flyway rodar a migracao, o que causaria erro
     * "Schema validation: missing table".
     *
     * <p>O metodo e {@code static} porque {@link BeanFactoryPostProcessor}
     * precisa estar disponivel antes da fase normal de criacao de beans.</p>
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
                dependsOn.add("flywayInitializer");
                bd.setDependsOn(dependsOn.toArray(new String[0]));
            }
        };
    }

    public static class FlywayInitializer {

        private final Flyway flyway;
        private final boolean repairOnStart;

        public FlywayInitializer(Flyway flyway, boolean repairOnStart) {
            this.flyway = flyway;
            this.repairOnStart = repairOnStart;
        }

        public void run() {
            if (repairOnStart) {
                log.warn("FLYWAY_REPAIR_ON_START=true - executando repair antes do migrate. "
                        + "Desligue essa flag apos a inicializacao bem-sucedida.");
                flyway.repair();
            }
            flyway.migrate();
        }
    }
}

package com.spectrumai.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Amarração de {@code spectrum.bigquery.*} em {@link AppProperties}.
 *
 * <p>Vale um teste porque a falha seria silenciosa: um componente do record que não
 * casa com o prefixo publicado fica nulo, sem erro de inicialização, e a integração
 * se comporta como se estivesse desligada — indistinguível de {@code enabled=false}
 * pelo lado de fora. Tranca também os nomes das chaves ({@code retention-days},
 * {@code auto-sync}, {@code batch-size}), que só se manifestam em runtime.
 */
class AppPropertiesBindingTest {

    private AppProperties bind(MapConfigurationPropertySource source) {
        return new Binder(source).bind("spectrum", Bindable.of(AppProperties.class)).get();
    }

    @Test
    @DisplayName("spectrum.bigquery.* preenche o componente bigquery")
    void bindsBigQueryProperties() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource();
        source.put("spectrum.bigquery.enabled", "true");
        source.put("spectrum.bigquery.project-id", "spectrum-ai-504812");
        source.put("spectrum.bigquery.dataset", "spectrum_analytics");
        source.put("spectrum.bigquery.table", "vehicle_specs");
        source.put("spectrum.bigquery.retention-days", "730");
        source.put("spectrum.bigquery.auto-sync", "true");
        source.put("spectrum.bigquery.batch-size", "500");

        AppProperties.BigQuery bigquery = bind(source).bigquery();

        assertThat(bigquery).isNotNull();
        assertThat(bigquery.enabled()).isTrue();
        assertThat(bigquery.projectId()).isEqualTo("spectrum-ai-504812");
        assertThat(bigquery.dataset()).isEqualTo("spectrum_analytics");
        assertThat(bigquery.table()).isEqualTo("vehicle_specs");
        assertThat(bigquery.retentionDays()).isEqualTo(730);
        assertThat(bigquery.autoSync()).isTrue();
        assertThat(bigquery.batchSize()).isEqualTo(500);
    }

    /**
     * Sem nenhuma chave do bloco, o componente fica nulo em vez de ganhar defaults —
     * é por isso que todo acesso a {@code properties.bigquery()} testa nulo antes de
     * ler qualquer campo.
     */
    @Test
    @DisplayName("sem configuração nenhuma, o componente fica nulo")
    void leavesBigQueryNullWhenAbsent() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource();
        source.put("spectrum.storage.gcs.bucket", "bucket-de-teste");

        assertThat(bind(source).bigquery()).isNull();
    }
}

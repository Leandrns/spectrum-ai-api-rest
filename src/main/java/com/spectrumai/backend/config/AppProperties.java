package com.spectrumai.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param bigquery nome todo minúsculo apenas para espelhar o prefixo publicado,
 *                 {@code spectrum.bigquery.*}. O relaxed binding ignora hífens na
 *                 comparação, então {@code bigQuery} amarraria igual — a escolha
 *                 aqui é de legibilidade, não de funcionamento.
 */
@ConfigurationProperties(prefix = "spectrum")
public record AppProperties(Ai ai, Fipe fipe, Storage storage, BigQuery bigquery) {

    public record Ai(String provider, Gemini gemini) {
        public record Gemini(String apiKey, String model, boolean groundingEnabled, int timeoutSeconds) {}
    }

    /**
     * Configuração da API FIPE (https://fipe.online/docs/api/fipe), utilizada
     * exclusivamente para popular o catálogo de veículos.
     */
    public record Fipe(String baseUrl, String apiToken, String vehicleType, int timeoutSeconds, int requestDelayMs, boolean filterSegments, int minYear) {}

    /**
     * Armazenamento dos arquivos exportados (CSV/PDF). Eles precisam sobreviver à
     * pesquisa que os originou, já que o histórico permite baixá-los depois.
     */
    public record Storage(Gcs gcs) {

        /**
         * Bucket do Google Cloud Storage. As credenciais vêm de Application Default
         * Credentials — em dev, aponte {@code GOOGLE_APPLICATION_CREDENTIALS} para o
         * JSON de uma service account com {@code roles/storage.objectAdmin} no bucket.
         *
         * <p>Em plataformas sem sistema de arquivos para secrets (Railway, Heroku),
         * use {@code credentialsJson} — o conteúdo do JSON direto na variável de
         * ambiente, em texto puro ou base64.
         *
         * @param signedUrlTtlMinutes validade da URL de download assinada
         * @param objectPrefix        pasta raiz dentro do bucket
         * @param credentialsJson     conteúdo do JSON da service account; vazio = usa ADC
         */
        public record Gcs(String bucket, String projectId, int signedUrlTtlMinutes, String objectPrefix,
                          String credentialsJson) {}
    }

    /**
     * Ingestão das fichas técnicas no BigQuery, para análise em ferramenta de BI.
     * Ao contrário da exportação em arquivo, aqui o dado não é entregue ao usuário
     * e sim empilhado numa tabela de fatos consultável.
     *
     * <p>O dataset precisa existir de antemão (a localização é imutável e por isso
     * é uma decisão de infra); a tabela e a view são criadas pela aplicação.
     *
     * @param enabled         desliga a integração inteira; endpoints respondem 503
     * @param dataset         dataset já criado no projeto, na mesma localização do bucket
     * @param table           tabela de fatos; criada no primeiro uso, particionada e clusterizada
     * @param retentionDays   expiração das partições, espelhando {@code retention.searches-days}
     * @param autoSync        sincroniza sozinho ao concluir cada pesquisa
     * @param batchSize       linhas por requisição de {@code insertAll}
     * @param credentialsJson vazio = reaproveita a credencial do Cloud Storage e, na falta dela, o ADC
     */
    public record BigQuery(boolean enabled, String projectId, String dataset, String table,
                           int retentionDays, boolean autoSync, int batchSize, String credentialsJson) {}
}

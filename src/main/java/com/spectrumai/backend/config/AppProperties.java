package com.spectrumai.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spectrum")
public record AppProperties(Ai ai, Fipe fipe, Storage storage) {

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
}

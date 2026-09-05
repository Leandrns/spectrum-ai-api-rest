package com.spectrumai.backend.common.gcp;

import com.google.auth.oauth2.ServiceAccountCredentials;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Leitura da service account do GCP a partir de uma variável de ambiente,
 * compartilhada por todos os clients Google da aplicação (Cloud Storage e BigQuery).
 *
 * <p>Existe para plataformas que não oferecem um arquivo de secret montado
 * (Railway, Heroku, Fly), onde o Application Default Credentials não tem de onde
 * ler a credencial. Quando a variável vem vazia, cada client cai no ADC —
 * {@code GOOGLE_APPLICATION_CREDENTIALS} ou o metadata server.
 */
public final class GcpCredentials {

    private GcpCredentials() {}

    /**
     * Converte o conteúdo de uma variável de ambiente em credencial.
     *
     * <p>Aceita o JSON em texto puro ou em base64: alguns painéis de configuração
     * maltratam valores multilinha, e o base64 contorna isso.
     *
     * <p>Exige uma service account de verdade — a assinatura de URL V4 do Cloud
     * Storage precisa da chave privada, então um JSON de outro tipo de credencial
     * não serviria.
     *
     * @param raw          conteúdo da variável; {@code null} ou vazio devolve {@code null}
     * @param propertyName nome da variável, usado apenas nas mensagens de erro
     * @return a credencial, ou {@code null} para indicar "use o ADC"
     */
    public static ServiceAccountCredentials fromJsonProperty(String raw, String propertyName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String json = raw.trim();
        if (!json.startsWith("{")) {
            try {
                json = new String(Base64.getDecoder().decode(json), StandardCharsets.UTF_8).trim();
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(propertyName + " não é JSON nem base64 válido.", e);
            }
        }
        try {
            return ServiceAccountCredentials.fromStream(
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new IllegalStateException(
                    propertyName + " não é um JSON de service account válido "
                            + "(precisa conter client_email e private_key).", e);
        }
    }
}

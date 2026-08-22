package com.spectrumai.backend.export.storage;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import com.spectrumai.backend.common.exception.BusinessException;
import com.spectrumai.backend.common.exception.ErrorCode;
import com.spectrumai.backend.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Armazenamento em Google Cloud Storage.
 *
 * <p>Duas formas de autenticar, nesta ordem: {@code GCP_CREDENTIALS_JSON} com o
 * conteúdo da service account (para plataformas sem arquivo de secret, como Railway)
 * ou Application Default Credentials via {@code GOOGLE_APPLICATION_CREDENTIALS}.
 *
 * <p>O client é construído na primeira utilização, e não no boot: sem isso, uma
 * instalação de desenvolvimento sem credencial GCP não conseguiria nem subir a
 * aplicação — a exportação é uma funcionalidade entre várias, e só ela deve falhar
 * quando o bucket não está configurado.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GcsExportStorage implements ExportStorage {

    private final AppProperties properties;

    private volatile Storage client;

    @Override
    public void upload(String objectName, byte[] content, String contentType, String downloadFilename) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucket(), objectName))
                .setContentType(contentType)
                .setContentDisposition("attachment; filename=\"" + sanitizeFilename(downloadFilename) + "\"")
                .build();
        try {
            client().create(blobInfo, content);
            log.info("Export enviado ao GCS: gs://{}/{} ({} bytes)", bucket(), objectName, content.length);
        } catch (StorageException e) {
            throw storageFailure("enviar o arquivo", objectName, e);
        }
    }

    @Override
    public boolean exists(String objectName) {
        try {
            return client().get(BlobId.of(bucket(), objectName)) != null;
        } catch (StorageException e) {
            // Indisponibilidade do bucket não deve ser lida como "objeto ausente":
            // isso faria o chamador reenviar o arquivo e falhar logo em seguida.
            throw storageFailure("consultar o arquivo", objectName, e);
        }
    }

    @Override
    public String signedUrl(String objectName, Duration ttl) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucket(), objectName)).build();
        try {
            return client().signUrl(blobInfo, ttl.toMinutes(), TimeUnit.MINUTES,
                    Storage.SignUrlOption.withV4Signature()).toString();
        } catch (IllegalStateException | StorageException e) {
            // IllegalStateException aqui costuma significar credencial sem chave privada:
            // a assinatura V4 precisa assinar localmente ou via IAM SignBlob.
            throw storageFailure("gerar a URL de download", objectName, e);
        }
    }

    private Storage client() {
        Storage local = this.client;
        if (local == null) {
            synchronized (this) {
                local = this.client;
                if (local == null) {
                    local = this.client = buildClient();
                }
            }
        }
        return local;
    }

    private Storage buildClient() {
        AppProperties.Storage.Gcs gcs = gcsProperties();
        StorageOptions.Builder builder = StorageOptions.newBuilder();
        if (gcs.projectId() != null && !gcs.projectId().isBlank()) {
            builder.setProjectId(gcs.projectId());
        }
        try {
            ServiceAccountCredentials inline = credentialsFromProperty(gcs.credentialsJson());
            if (inline != null) {
                builder.setCredentials(inline);
                log.info("GCS autenticado pela credencial inline (service account {})",
                        inline.getClientEmail());
            }
            // Sem credencial inline, cai no Application Default Credentials
            // (GOOGLE_APPLICATION_CREDENTIALS ou metadata server).
            return builder.build().getService();
        } catch (RuntimeException e) {
            log.error("Falha ao inicializar o client do GCS", e);
            throw new BusinessException(
                    "Armazenamento de exportações indisponível.",
                    HttpStatus.BAD_GATEWAY,
                    ErrorCode.STORAGE_ERROR);
        }
    }

    /**
     * Lê a service account de uma variável de ambiente, para plataformas que não
     * oferecem um arquivo de secret montado (Railway, Heroku, Fly).
     *
     * <p>Aceita o JSON em texto puro ou em base64: alguns painéis de configuração
     * maltratam valores multilinha, e o base64 contorna isso.
     *
     * <p>Exige uma service account de verdade — a assinatura de URL V4 precisa da
     * chave privada, então um JSON de outro tipo de credencial não serviria.
     */
    private ServiceAccountCredentials credentialsFromProperty(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String json = raw.trim();
        if (!json.startsWith("{")) {
            try {
                json = new String(Base64.getDecoder().decode(json), StandardCharsets.UTF_8).trim();
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "GCP_CREDENTIALS_JSON não é JSON nem base64 válido.", e);
            }
        }
        try {
            return ServiceAccountCredentials.fromStream(
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "GCP_CREDENTIALS_JSON não é um JSON de service account válido "
                            + "(precisa conter client_email e private_key).", e);
        }
    }

    private String bucket() {
        String bucket = gcsProperties().bucket();
        if (bucket == null || bucket.isBlank()) {
            log.error("spectrum.storage.gcs.bucket não configurado — defina GCS_EXPORT_BUCKET");
            throw new BusinessException(
                    "Armazenamento de exportações não configurado.",
                    HttpStatus.BAD_GATEWAY,
                    ErrorCode.STORAGE_ERROR);
        }
        return bucket;
    }

    private AppProperties.Storage.Gcs gcsProperties() {
        if (properties.storage() == null || properties.storage().gcs() == null) {
            throw new BusinessException(
                    "Armazenamento de exportações não configurado.",
                    HttpStatus.BAD_GATEWAY,
                    ErrorCode.STORAGE_ERROR);
        }
        return properties.storage().gcs();
    }

    /** Mensagem genérica ao cliente; o detalhe da API do Google fica só no log. */
    private BusinessException storageFailure(String acao, String objectName, Exception cause) {
        log.error("Falha ao {} no GCS: objeto={}", acao, objectName, cause);
        return new BusinessException(
                "Não foi possível " + acao + " da exportação. Tente novamente em instantes.",
                HttpStatus.BAD_GATEWAY,
                ErrorCode.STORAGE_ERROR);
    }

    /** Evita quebrar o header Content-Disposition com aspas ou quebras de linha. */
    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[\"\r\n]", "_");
    }
}

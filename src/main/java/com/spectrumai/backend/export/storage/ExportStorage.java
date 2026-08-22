package com.spectrumai.backend.export.storage;

import java.time.Duration;

/**
 * Armazenamento durável dos arquivos exportados. Os arquivos precisam sobreviver à
 * pesquisa que os originou, já que o histórico permite baixá-los muito depois.
 */
public interface ExportStorage {

    /**
     * Envia o arquivo, sobrescrevendo o objeto se ele já existir.
     *
     * @param downloadFilename nome sugerido ao navegador no momento do download
     */
    void upload(String objectName, byte[] content, String contentType, String downloadFilename);

    /** Verifica se o objeto ainda está lá — o cache não pode apontar para um objeto removido. */
    boolean exists(String objectName);

    /** URL temporária de download direto, sem passar pelo backend. */
    String signedUrl(String objectName, Duration ttl);
}

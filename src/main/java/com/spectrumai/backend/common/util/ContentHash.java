package com.spectrumai.backend.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 em hexadecimal do conteúdo gerado por uma exportação.
 *
 * <p>Não é hash criptográfico de segurança: serve para responder "este conteúdo é o
 * mesmo da última vez?" sem guardar o conteúdo. É o que permite reaproveitar um
 * arquivo já no bucket e não reenviar linhas idênticas ao BigQuery.
 */
public final class ContentHash {

    private ContentHash() {}

    public static String of(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }

    public static String of(String content) {
        return of(content.getBytes(StandardCharsets.UTF_8));
    }
}

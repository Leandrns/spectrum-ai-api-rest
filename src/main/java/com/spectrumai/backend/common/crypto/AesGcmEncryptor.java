package com.spectrumai.backend.common.crypto;

import com.spectrumai.backend.config.SecurityProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifragem sim�trica AES-GCM 256 bits para dados em repouso. Chave deve
 * ser provida via {@code DATA_ENCRYPTION_KEY} (base64 de 32 bytes).
 *
 * <p>Formato do ciphertext: {@code base64(IV || ciphertext || tag)}.
 * Cada chamada usa IV aleat�rio de 12 bytes (recomenda��o NIST para GCM).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AesGcmEncryptor {

    private static final String ALG = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LEN_BYTES = 12;
    private static final int TAG_LEN_BITS = 128;

    private final SecurityProperties securityProperties;
    private final SecureRandom random = new SecureRandom();
    private SecretKey key;

    @PostConstruct
    void init() {
        String keyB64 = securityProperties.encryption().aesKey();
        if (keyB64 == null || keyB64.isBlank()) {
            log.warn("DATA_ENCRYPTION_KEY n�o configurada: campos sens�veis n�o ser�o cifrados em repouso. "
                    + "Em produ��o exporte uma chave AES-256 (32 bytes base64).");
            this.key = null;
            return;
        }
        byte[] decoded = Base64.getDecoder().decode(keyB64);
        if (decoded.length != 32) {
            throw new IllegalStateException(
                    "DATA_ENCRYPTION_KEY inv�lida: esperados 32 bytes (AES-256), recebidos " + decoded.length);
        }
        this.key = new SecretKeySpec(decoded, ALG);
    }

    public boolean isEnabled() {
        return key != null;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        if (!isEnabled()) return plaintext;
        try {
            byte[] iv = new byte[IV_LEN_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LEN_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct);
            return "enc:v1:" + Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao cifrar dado em repouso", e);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null) return null;
        if (!ciphertext.startsWith("enc:v1:")) {
            // Valor legado n�o cifrado � devolvido como est� (backward-compat)
            return ciphertext;
        }
        if (!isEnabled()) {
            throw new IllegalStateException("Dado cifrado sem chave configurada: defina DATA_ENCRYPTION_KEY");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext.substring("enc:v1:".length()));
            ByteBuffer buf = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LEN_BYTES];
            buf.get(iv);
            byte[] ct = new byte[buf.remaining()];
            buf.get(ct);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LEN_BITS, iv));
            return new String(cipher.doFinal(ct), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao decifrar dado em repouso", e);
        }
    }
}

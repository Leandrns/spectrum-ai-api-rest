package com.spectrumai.backend.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Converter JPA que aplica {@link AesGcmEncryptor} em campos String anotados com
 * {@code @Convert(converter = EncryptedStringConverter.class)}.
 *
 * <p>JPA instancia o converter via {@code new}, fora do contexto Spring. Por isso
 * o encryptor � injetado via bridge est�tico ({@link AesGcmEncryptorHolder}).</p>
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        AesGcmEncryptor enc = AesGcmEncryptorHolder.get();
        return enc == null ? attribute : enc.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        AesGcmEncryptor enc = AesGcmEncryptorHolder.get();
        return enc == null ? dbData : enc.decrypt(dbData);
    }

    @Component
    public static class AesGcmEncryptorHolder {

        private static AesGcmEncryptor instance;

        @Autowired
        public AesGcmEncryptorHolder(AesGcmEncryptor encryptor) {
            instance = encryptor;
        }

        public static AesGcmEncryptor get() {
            return instance;
        }
    }
}

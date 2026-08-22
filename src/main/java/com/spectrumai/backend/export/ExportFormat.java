package com.spectrumai.backend.export;

import com.spectrumai.backend.common.exception.BusinessException;
import com.spectrumai.backend.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Locale;

/** Formatos de exportação suportados pelo contrato de API. */
public enum ExportFormat {

    CSV("text/csv; charset=utf-8", "csv"),
    PDF("application/pdf", "pdf");

    private final String contentType;
    private final String extension;

    ExportFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    /** Converte o query param {@code format} em enum, respondendo 400 em valor desconhecido. */
    public static ExportFormat from(String value) {
        if (value == null || value.isBlank()) {
            return CSV;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "Formato de exportação inválido: " + value + ". Use 'csv' ou 'pdf'.",
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR);
        }
    }
}

package com.spectrumai.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Formato padronizado de erro do contrato:
 * <pre>
 * {
 *   "status": 401,
 *   "error":  "UNAUTHORIZED",
 *   "message":"Token expirado.",
 *   "timestamp":"2026-04-30T14:00:00Z",
 *   "path":"/v1/searches"
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        int status,
        String error,
        String message,
        OffsetDateTime timestamp,
        String path,
        List<FieldError> fields
) {

    public ApiError(int status, String error, String message, OffsetDateTime timestamp, String path) {
        this(status, error, message, timestamp, path, null);
    }

    public record FieldError(String field, String message) {}
}

package com.spectrumai.backend.common.exception;

/** Códigos de erro do contrato de API. */
public final class ErrorCode {

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String UNAUTHORIZED     = "UNAUTHORIZED";
    public static final String FORBIDDEN        = "FORBIDDEN";
    public static final String NOT_FOUND        = "NOT_FOUND";
    public static final String RATE_LIMITED     = "RATE_LIMITED";
    public static final String INTERNAL_ERROR   = "INTERNAL_ERROR";

    private ErrorCode() {}
}

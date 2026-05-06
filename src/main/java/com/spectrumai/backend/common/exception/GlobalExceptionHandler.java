package com.spectrumai.backend.common.exception;

import com.spectrumai.backend.common.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus()).body(new ApiError(
                ex.getStatus().value(),
                ex.getErrorCode(),
                ex.getMessage(),
                OffsetDateTime.now(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ApiError.FieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ApiError(
                400,
                ErrorCode.VALIDATION_ERROR,
                "Campos inválidos",
                OffsetDateTime.now(),
                request.getRequestURI(),
                fields
        ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ApiError(
                400,
                ErrorCode.VALIDATION_ERROR,
                ex.getMessage(),
                OffsetDateTime.now(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError(
                401,
                ErrorCode.UNAUTHORIZED,
                ex.getMessage(),
                OffsetDateTime.now(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(
                403,
                ErrorCode.FORBIDDEN,
                ex.getMessage(),
                OffsetDateTime.now(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Erro não tratado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(
                500,
                ErrorCode.INTERNAL_ERROR,
                "Erro inesperado",
                OffsetDateTime.now(),
                request.getRequestURI()
        ));
    }
}

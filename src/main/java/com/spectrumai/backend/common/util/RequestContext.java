package com.spectrumai.backend.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Helpers para extrair metadados da requisi��o HTTP atual. */
public final class RequestContext {

    private RequestContext() {}

    public static String clientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) return "unknown";
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public static String userAgent() {
        HttpServletRequest request = currentRequest();
        if (request == null) return null;
        return request.getHeader("User-Agent");
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }
}

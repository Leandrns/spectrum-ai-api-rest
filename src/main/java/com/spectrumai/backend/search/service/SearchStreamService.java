package com.spectrumai.backend.search.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

public interface SearchStreamService {

    SseEmitter subscribe(UUID searchId);

    void publishStatus(UUID searchId, String status, String detail);
}

package com.spectrumai.backend.search.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@Service
public class SearchStreamServiceImpl implements SearchStreamService {

    @Override
    public SseEmitter subscribe(UUID searchId) {
        throw new UnsupportedOperationException("SearchStreamService.subscribe ainda não implementado");
    }

    @Override
    public void publishStatus(UUID searchId, String status, String detail) {
        throw new UnsupportedOperationException("SearchStreamService.publishStatus ainda não implementado");
    }
}

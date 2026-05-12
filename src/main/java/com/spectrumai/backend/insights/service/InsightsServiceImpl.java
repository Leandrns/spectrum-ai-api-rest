package com.spectrumai.backend.insights.service;

import com.spectrumai.backend.insights.dto.ChatMessage;
import com.spectrumai.backend.insights.dto.InsightsReport;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class InsightsServiceImpl implements InsightsService {

    @Override
    public InsightsReport generateForSession(UUID sessionId) {
        throw new UnsupportedOperationException("InsightsService.generateForSession ainda não implementado");
    }

    @Override
    public ChatMessage chat(UUID sessionId, String userMessage) {
        throw new UnsupportedOperationException("InsightsService.chat ainda não implementado");
    }
}

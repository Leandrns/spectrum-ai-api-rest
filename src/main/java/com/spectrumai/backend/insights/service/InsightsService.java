package com.spectrumai.backend.insights.service;

import com.spectrumai.backend.insights.dto.ChatMessage;
import com.spectrumai.backend.insights.dto.InsightsReport;

import java.util.UUID;

public interface InsightsService {

    InsightsReport generateForSession(UUID sessionId);

    ChatMessage chat(UUID sessionId, String userMessage);
}

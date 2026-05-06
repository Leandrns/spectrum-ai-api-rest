package com.spectrumai.backend.ai.service;

import com.spectrumai.backend.ai.dto.AiResponse;
import com.spectrumai.backend.search.dto.SearchRequest;

public interface AiOrchestrationService {

    AiResponse runSpecSearch(SearchRequest request);

    AiResponse runInsights(java.util.UUID sessionId);
}

package com.spectrumai.backend.ai.provider;

import com.spectrumai.backend.ai.dto.AiRequest;
import com.spectrumai.backend.ai.dto.AiResponse;

public interface AiProvider {

    String name();

    AiResponse generate(AiRequest request);
}

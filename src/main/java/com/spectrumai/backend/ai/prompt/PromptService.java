package com.spectrumai.backend.ai.prompt;

import java.util.Map;

public interface PromptService {

    String render(String promptName, Map<String, Object> variables);
}

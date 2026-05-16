package com.spectrumai.backend.ai.prompt;

import com.spectrumai.backend.common.exception.BusinessException;
import com.spectrumai.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PromptServiceImpl implements PromptService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

    private final PromptRepository promptRepository;

    @Override
    @Transactional(readOnly = true)
    public String render(String promptName, Map<String, Object> variables) {
        PromptTemplate template = promptRepository.findByNameAndActiveTrue(promptName)
                .orElseThrow(() -> new BusinessException(
                        "Prompt não encontrado ou inativo: " + promptName,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        ErrorCode.INTERNAL_ERROR));

        Matcher matcher = PLACEHOLDER.matcher(template.getBody());
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = variables == null ? null : variables.get(key);
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : value.toString()));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}

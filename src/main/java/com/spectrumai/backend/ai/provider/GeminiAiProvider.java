package com.spectrumai.backend.ai.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spectrumai.backend.ai.dto.AiRequest;
import com.spectrumai.backend.ai.dto.AiResponse;
import com.spectrumai.backend.common.exception.BusinessException;
import com.spectrumai.backend.common.exception.ErrorCode;
import com.spectrumai.backend.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provider Gemini via API REST (https://generativelanguage.googleapis.com).
 *
 * <p>Quando {@link AiRequest#groundingEnabled()} é {@code true} a chamada
 * inclui a ferramenta {@code googleSearchRetrieval}, que ativa o Grounding
 * nativo do Gemini 1.5 — respostas vêm acompanhadas de {@code groundingMetadata}
 * com URLs das fontes consultadas. Essas URLs são propagadas no
 * {@link AiResponse#citations()}.
 */
@Slf4j
@Component
public class GeminiAiProvider implements AiProvider {

    private static final String GENERATE_PATH = "/v1beta/models/{model}:generateContent";
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
    private static final Pattern JSON_FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.MULTILINE);

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final boolean configured;

    public GeminiAiProvider(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        AppProperties.Ai.Gemini gemini = properties.ai().gemini();
        this.configured = gemini.apiKey() != null && !gemini.apiKey().isBlank();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(gemini.timeoutSeconds() <= 0 ? 60 : gemini.timeoutSeconds());
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);

        this.restClient = RestClient.builder()
                .baseUrl(DEFAULT_BASE_URL)
                .requestFactory(factory)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public AiResponse generate(AiRequest request) {
        if (!configured) {
            throw new BusinessException(
                    "GEMINI_API_KEY não configurada.",
                    HttpStatus.PRECONDITION_FAILED,
                    ErrorCode.INTERNAL_ERROR);
        }

        AppProperties.Ai.Gemini gemini = properties.ai().gemini();
        ObjectNode body = buildRequestBody(request);

        // Serializa para String para evitar que o RestClient criado manualmente
        // caia no serializer POJO do Jackson ao receber um JsonNode como body.
        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new BusinessException(
                    "Erro ao serializar request para o Gemini: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.INTERNAL_ERROR);
        }

        long started = System.currentTimeMillis();
        String rawResponse;
        try {
            // exchange() lê bytes brutos independente do Content-Type da resposta,
            // evitando falhas quando o Gemini retorna application/octet-stream.
            rawResponse = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(GENERATE_PATH)
                            .queryParam("key", gemini.apiKey())
                            .build(gemini.model()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .exchange((req, res) -> {
                        byte[] bytes = res.getBody().readAllBytes();
                        String responseText = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                        if (res.getStatusCode().isError()) {
                            throw new BusinessException(
                                    "Gemini retornou " + res.getStatusCode() + ": " + responseText,
                                    HttpStatus.BAD_GATEWAY,
                                    ErrorCode.INTERNAL_ERROR);
                        }
                        return responseText;
                    });
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BusinessException(
                    "Falha de comunicação com Gemini: " + e.getMessage(),
                    HttpStatus.BAD_GATEWAY,
                    ErrorCode.INTERNAL_ERROR);
        }

        long latency = System.currentTimeMillis() - started;

        if (rawResponse == null || rawResponse.isBlank()) {
            throw new BusinessException(
                    "Gemini retornou corpo vazio.",
                    HttpStatus.BAD_GATEWAY,
                    ErrorCode.INTERNAL_ERROR);
        }

        JsonNode responseNode;
        try {
            responseNode = objectMapper.readTree(rawResponse);
        } catch (JsonProcessingException e) {
            throw new BusinessException(
                    "Gemini retornou resposta não-JSON: " + e.getMessage(),
                    HttpStatus.BAD_GATEWAY,
                    ErrorCode.INTERNAL_ERROR);
        }

        JsonNode candidate = responseNode.path("candidates").path(0);
        String text = extractText(candidate);
        JsonNode structured = parseStructuredOutput(text);
        resolveSourceUrls(structured);
        List<AiResponse.Citation> citations = extractCitations(candidate);
        int tokens = responseNode.path("usageMetadata").path("totalTokenCount").asInt(0);

        log.info("Gemini respondeu em {}ms (tokens={}, citations={})", latency, tokens, citations.size());
        return new AiResponse(structured, citations, latency, tokens);
    }

    private ObjectNode buildRequestBody(AiRequest request) {
        ObjectNode root = objectMapper.createObjectNode();

        ArrayNode contents = root.putArray("contents");
        ObjectNode content = contents.addObject();
        content.put("role", "user");
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", request.prompt());

        ObjectNode generationConfig = root.putObject("generationConfig");
        generationConfig.put("temperature", 0.2);
        // Quando há grounding ativo, o Gemini 1.5 não aceita responseSchema/responseMimeType=json;
        // a estrutura é induzida pelo prompt e parseada do texto retornado.
        if (!request.groundingEnabled() && request.responseSchemaJson() != null
                && !request.responseSchemaJson().isBlank()) {
            generationConfig.put("responseMimeType", "application/json");
            try {
                generationConfig.set("responseSchema", objectMapper.readTree(request.responseSchemaJson()));
            } catch (JsonProcessingException e) {
                throw new BusinessException(
                        "Response schema inválido para Gemini: " + e.getMessage(),
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        ErrorCode.INTERNAL_ERROR);
            }
        }

        if (request.groundingEnabled()) {
            ArrayNode tools = root.putArray("tools");
            // Gemini 2.x usa "google_search"; Gemini 1.x usa "googleSearchRetrieval"
            String model = properties.ai().gemini().model();
            if (model != null && model.contains("gemini-2")) {
                tools.addObject().putObject("google_search");
            } else {
                tools.addObject().putObject("googleSearchRetrieval");
            }
        }

        return root;
    }

    private String extractText(JsonNode candidate) {
        if (candidate == null || candidate.isMissingNode()) {
            throw new BusinessException(
                    "Gemini não retornou candidatos.",
                    HttpStatus.BAD_GATEWAY,
                    ErrorCode.INTERNAL_ERROR);
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            String text = part.path("text").asText(null);
            if (text != null) {
                sb.append(text);
            }
        }
        String result = sb.toString().trim();
        if (result.isEmpty()) {
            throw new BusinessException(
                    "Gemini retornou conteúdo vazio.",
                    HttpStatus.BAD_GATEWAY,
                    ErrorCode.INTERNAL_ERROR);
        }
        return result;
    }

    /**
     * O Gemini, mesmo instruído a devolver JSON puro, eventualmente embrulha o
     * resultado em cercas markdown (```json ... ```). Removemos a cerca antes
     * de fazer o parse.
     */
    private JsonNode parseStructuredOutput(String text) {
        String json = text;
        Matcher matcher = JSON_FENCE.matcher(text);
        if (matcher.find()) {
            json = matcher.group(1).trim();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            log.error("Falha ao parsear JSON do Gemini. Conteúdo bruto: {}", text);
            throw new BusinessException(
                    "Gemini retornou JSON inválido.",
                    HttpStatus.BAD_GATEWAY,
                    ErrorCode.INTERNAL_ERROR);
        }
    }

    private List<AiResponse.Citation> extractCitations(JsonNode candidate) {
        List<AiResponse.Citation> citations = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode chunk : candidate.path("groundingMetadata").path("groundingChunks")) {
            JsonNode web = chunk.path("web");
            String uri = web.path("uri").asText(null);
            String title = web.path("title").asText(null);
            if (uri != null && seen.add(uri)) {
                String resolvedUri = resolveRedirect(uri);
                citations.add(new AiResponse.Citation(resolvedUri, title));
            }
        }
        return citations;
    }

    /**
     * Percorre recursivamente o JsonNode de specs e resolve cada campo "sourceUrl"
     * que aponte para o proxy do Vertex AI, substituindo pelo URL real da fonte.
     */
    private void resolveSourceUrls(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            JsonNode sourceUrl = obj.get("sourceUrl");
            if (sourceUrl != null && sourceUrl.isTextual()) {
                String url = sourceUrl.asText();
                if (url.contains("vertexaisearch.cloud.google.com")) {
                    String resolved = resolveRedirect(url);
                    if (resolved != null) {
                        obj.put("sourceUrl", resolved);
                    } else {
                        obj.putNull("sourceUrl");
                    }
                }
            }
            obj.fields().forEachRemaining(entry -> resolveSourceUrls(entry.getValue()));
        } else if (node.isArray()) {
            node.forEach(this::resolveSourceUrls);
        }
    }

    /**
     * Segue o redirect das URLs de proxy do Vertex AI para obter a URL real da fonte.
     * Faz apenas um HEAD sem seguir redirects automaticamente, capturando o header
     * {@code Location}. Retorna {@code null} se a URL expirou ou a resolução falhar.
     */
    private String resolveRedirect(String url) {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    java.net.URI.create(url).toURL().openConnection();
            conn.setRequestMethod("HEAD");
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(4_000);
            conn.setReadTimeout(4_000);
            conn.connect();
            int status = conn.getResponseCode();
            if (status == java.net.HttpURLConnection.HTTP_MOVED_TEMP
                    || status == java.net.HttpURLConnection.HTTP_MOVED_PERM
                    || status == 307 || status == 308) {
                String location = conn.getHeaderField("Location");
                if (location != null && !location.isBlank()) {
                    log.debug("Redirect resolvido: {} → {}", url, location);
                    return location;
                }
            }
            log.warn("Redirect não resolvido para {} (status={})", url, status);
            return null;
        } catch (Exception e) {
            log.warn("Falha ao resolver redirect de citation {}: {}", url, e.getMessage());
            return null;
        }
    }
}

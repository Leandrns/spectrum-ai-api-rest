package com.spectrumai.backend.ai.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.genai.Client;
import com.google.genai.types.*;
import com.spectrumai.backend.ai.dto.AiRequest;
import com.spectrumai.backend.ai.dto.AiResponse;
import com.spectrumai.backend.common.exception.BusinessException;
import com.spectrumai.backend.common.exception.ErrorCode;
import com.spectrumai.backend.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provider Gemini usando o SDK oficial {@code com.google.genai:google-genai}.
 * O SDK lê {@code GEMINI_API_KEY} do ambiente automaticamente, gerencia a
 * serialização da request e oferece acesso tipado ao {@code groundingMetadata}.
 */
@Slf4j
@Component
public class GeminiAiProvider implements AiProvider {

    private static final Pattern JSON_FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.MULTILINE);

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final Client geminiClient;
    private final HttpClient httpClient;

    public GeminiAiProvider(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.geminiClient = Client.builder()
                .apiKey(properties.ai().gemini().apiKey())
                .httpOptions(HttpOptions.builder()
                        .apiVersion("v1beta")
                        .build())
                .build();
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(4))
                .build();
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public AiResponse generate(AiRequest request) {
        AppProperties.Ai.Gemini gemini = properties.ai().gemini();
        GenerateContentConfig config = buildConfig(request, gemini.model());

        long started = System.currentTimeMillis();
        GenerateContentResponse response;
        try {
            response = geminiClient.models.generateContent(gemini.model(), request.prompt(), config);
        } catch (Exception e) {
            throw new BusinessException(
                    "Falha ao chamar Gemini: " + e.getMessage(),
                    HttpStatus.BAD_GATEWAY,
                    ErrorCode.INTERNAL_ERROR);
        }
        long latency = System.currentTimeMillis() - started;

        String text = response.text();
        if (text == null || text.isBlank()) {
            throw new BusinessException(
                    "Gemini retornou conteúdo vazio.",
                    HttpStatus.BAD_GATEWAY,
                    ErrorCode.INTERNAL_ERROR);
        }

        JsonNode structured = parseStructuredOutput(text);
        List<AiResponse.Citation> citations = extractCitations(response);
        injectSources(structured, citations);

        int tokens = response.usageMetadata()
                .flatMap(u -> u.totalTokenCount())
                .orElse(0);

        log.info("Gemini respondeu em {}ms (tokens={}, citations={})", latency, tokens, citations.size());
        return new AiResponse(structured, citations, latency, tokens);
    }

    private GenerateContentConfig buildConfig(AiRequest request, String model) {
        GenerateContentConfig.Builder builder = GenerateContentConfig.builder();

        if (request.groundingEnabled()) {
            Tool tool;
            if (model.contains("gemini-2") || model.contains("gemini-3")) {
                // Modelos 2.x+: GoogleSearch não expõe DynamicRetrievalConfig na API —
                // o modelo decide quando acionar a busca. A instrução no prompt
                // é o único mecanismo para forçar o grounding nessa família.
                tool = Tool.builder().googleSearch(GoogleSearch.builder().build()).build();
            } else {
                // Modelos 1.x: DynamicRetrievalConfig com threshold 0.0 força
                // a busca em 100% das chamadas, independente da confiança interna.
                tool = Tool.builder()
                        .googleSearchRetrieval(GoogleSearchRetrieval.builder()
                                .dynamicRetrievalConfig(DynamicRetrievalConfig.builder()
                                        .dynamicThreshold(0.0f)
                                        .build())
                                .build())
                        .build();
            }
            builder.tools(List.of(tool));
        }

        return builder.build();
    }

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

    private List<AiResponse.Citation> extractCitations(GenerateContentResponse response) {
        List<AiResponse.Citation> citations = new ArrayList<>();

        List<Candidate> candidates = response.candidates().orElse(List.of());
        if (candidates.isEmpty()) return citations;

        Optional<GroundingMetadata> metadataOpt = candidates.getFirst().groundingMetadata();
        if (metadataOpt.isEmpty()) return citations;

        GroundingMetadata metadata = metadataOpt.get();
        log.info("groundingMetadata recebido: webSearchQueries={}, groundingChunks={}, searchEntryPoint={}",
                metadata.webSearchQueries().orElse(List.of()),
                metadata.groundingChunks().map(chunks -> chunks.stream()
                        .map(c -> c.web().map(w -> w.uri().orElse("?")).orElse("(sem web)"))
                        .toList()).orElse(List.of()),
                metadata.searchEntryPoint()
                        .flatMap(sep -> sep.renderedContent())
                        .map(html -> "[renderedContent length=" + html.length() + "]")
                        .orElse("(ausente)"));
        Set<String> seen = new HashSet<>();

        // Gemini 1.x e 2.x quando retorna groundingChunks (fontes por página)
        metadata.groundingChunks().ifPresent(chunks -> {
            for (GroundingChunk chunk : chunks) {
                chunk.web().ifPresent(web -> {
                    String uri   = web.uri().orElse(null);
                    String title = web.title().orElse(null);
                    if (uri != null && seen.add(uri)) {
                        String resolved = resolveRedirect(uri);
                        citations.add(new AiResponse.Citation(resolved != null ? resolved : uri, title));
                    }
                });
            }
        });

        if (citations.isEmpty()) {
            log.warn("Nenhuma citation extraída do groundingMetadata — groundingChunks ausente na resposta");
        }
        return citations;
    }

    private static final String NO_GROUNDING_MESSAGE =
            "Busca completamente feita por conhecimento da IA";

    private void injectSources(JsonNode root, List<AiResponse.Citation> citations) {
        if (!(root instanceof ObjectNode obj)) return;
        if (citations.isEmpty()) {
            // Sem groundingChunks: o modelo respondeu só com conhecimento interno.
            // Marcar isso explicitamente é importante para o frontend distinguir
            // de uma resposta com citations reais.
            obj.put("sources", NO_GROUNDING_MESSAGE);
            return;
        }
        ArrayNode sources = obj.putArray("sources");
        for (AiResponse.Citation citation : citations) {
            ObjectNode source = sources.addObject();
            source.put("url", citation.sourceUrl());
            source.put("title", citation.snippet());
        }
    }

    /**
     * Segue toda a cadeia de redirects da URL de proxy do Vertex AI e devolve a URL
     * final da fonte. O proxy costuma fazer 302 → site real, e em alguns casos o
     * site real ainda emite redirects próprios (canonicalização, idioma, etc.) —
     * o {@link HttpClient} com {@link HttpClient.Redirect#ALWAYS} resolve tudo de
     * uma vez. Retorna {@code null} quando a URL expirou ou a resolução falhou.
     */
    private String resolveRedirect(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "Mozilla/5.0 (compatible; SpectrumAI/1.0)")
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            String finalUrl = response.uri().toString();
            if (finalUrl.equals(url)) {
                log.warn("Redirect não resolvido para {} (status={})", url, response.statusCode());
                return null;
            }
            log.debug("Redirect resolvido: {} → {}", url, finalUrl);
            return finalUrl;
        } catch (Exception e) {
            log.warn("Falha ao resolver redirect de citation {}: {}", url, e.getMessage());
            return null;
        }
    }
}

package com.datanexus.datanexus.service.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

/**
 * Anthropic Claude AI provider implementation with streaming support.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClaudeProvider implements AIProvider {

    @Value("${ai.claude.api-key:}")
    private String apiKey;

    @Value("${ai.claude.model:claude-3-5-sonnet-20241022}")
    private String model;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public String getName() {
        return "claude";
    }

    @Override
    public boolean supportsClarification() {
        return true;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public AIResponse chat(AIRequest request) {
        if (!isConfigured()) {
            throw new IllegalStateException("Claude provider is not configured. Please set ai.claude.api-key");
        }

        try {
            String prompt = AIPromptBuilder.buildPrompt(request);
            String responseJson = callClaudeAPI(prompt);
            return parseClaudeResponse(responseJson);

        } catch (Exception e) {
            log.error("Failed to call Claude API: {}", e.getMessage(), e);
            return AIResponse.builder()
                    .type(AIResponseType.DIRECT_ANSWER)
                    .content("I encountered an error while processing your request: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public AIResponse streamChat(AIRequest request, StreamChunkHandler chunkHandler) {
        if (!isConfigured()) {
            throw new IllegalStateException("Claude provider is not configured. Please set ai.claude.api-key");
        }

        try {
            String prompt = AIPromptBuilder.buildPrompt(request);
            String fullText = streamClaudeAPI(prompt, chunkHandler);
            return AIResponseParser.parse(fullText, objectMapper);

        } catch (Exception e) {
            log.error("Failed to stream Claude API: {}", e.getMessage(), e);
            return AIResponse.builder()
                    .type(AIResponseType.DIRECT_ANSWER)
                    .content("I encountered an error while processing your request: " + e.getMessage())
                    .build();
        }
    }

    // ── Non-streaming API call ──────────────────────────────────────────

    private String callClaudeAPI(String prompt) throws Exception {
        String url = "https://api.anthropic.com/v1/messages";

        Map<String, Object> payload = Map.of(
                "model", model,
                "max_tokens", 4096,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Claude API error: " + response.statusCode() + " - " + response.body());
        }

        return response.body();
    }

    private AIResponse parseClaudeResponse(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        String aiText = root.path("content").get(0).path("text").asText();
        return AIResponseParser.parse(aiText, objectMapper);
    }

    // ── Streaming API call ──────────────────────────────────────────────

    /**
     * Calls Claude Messages API with stream:true.
     * SSE events: message_start, content_block_start, content_block_delta,
     * content_block_stop, message_delta, message_stop.
     * Text chunks are in content_block_delta events under delta.text.
     */
    private String streamClaudeAPI(String prompt, StreamChunkHandler chunkHandler) throws Exception {
        String url = "https://api.anthropic.com/v1/messages";

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("max_tokens", 4096);
        payload.put("stream", true);
        payload.put("messages", List.of(
                Map.of("role", "user", "content", prompt)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<Stream<String>> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofLines());

        if (response.statusCode() != 200) {
            String errorBody = String.join("\n", response.body().toList());
            throw new RuntimeException("Claude streaming API error: " + response.statusCode() + " - " + errorBody);
        }

        StringBuilder accumulated = new StringBuilder();

        response.body().forEach(line -> {
            if (line.startsWith("data: ")) {
                String jsonData = line.substring(6).trim();
                if (jsonData.isEmpty())
                    return;
                try {
                    JsonNode event = objectMapper.readTree(jsonData);
                    String eventType = event.path("type").asText("");

                    if ("content_block_delta".equals(eventType)) {
                        String text = event.path("delta").path("text").asText("");
                        if (!text.isEmpty()) {
                            accumulated.append(text);
                            chunkHandler.onChunk(text);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Skipping non-parseable Claude SSE line: {}", jsonData);
                }
            }
        });

        return accumulated.toString();
    }
}

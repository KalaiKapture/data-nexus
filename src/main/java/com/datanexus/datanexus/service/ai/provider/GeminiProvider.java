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
 * Google Gemini AI provider implementation with streaming support.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiProvider implements AIProvider {

    @Value("${ai.gemini.api-key:}")
    private String apiKey;

    @Value("${ai.gemini.model:gemini-2.0-flash-exp}")
    private String model;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public String getName() {
        return "gemini";
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
            throw new IllegalStateException("Gemini provider is not configured. Please set ai.gemini.api-key");
        }

        try {
            String prompt = AIPromptBuilder.buildPrompt(request,true);
            String responseJson = callGeminiAPI(prompt);
            return parseGeminiResponse(responseJson);

        } catch (Exception e) {
            log.error("Failed to call Gemini API: {}", e.getMessage(), e);
            return AIResponse.builder()
                    .type(AIResponseType.DIRECT_ANSWER)
                    .content("I encountered an error while processing your request: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public AIResponse streamChat(AIRequest request, StreamChunkHandler chunkHandler) {
        if (!isConfigured()) {
            throw new IllegalStateException("Gemini provider is not configured. Please set ai.gemini.api-key");
        }

        try {
            String prompt = AIPromptBuilder.buildPrompt(request,true);
            String fullText = streamGeminiAPI(prompt, chunkHandler);
            return AIResponseParser.parse(fullText, objectMapper);

        } catch (Exception e) {
            log.error("Failed to stream Gemini API: {}", e.getMessage(), e);
            return AIResponse.builder()
                    .type(AIResponseType.DIRECT_ANSWER)
                    .content("I encountered an error while processing your request: " + e.getMessage())
                    .build();
        }
    }

    // ── Non-streaming API call ──────────────────────────────────────────

    private String callGeminiAPI(String prompt) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey;

        Map<String, Object> payload = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "responseMimeType", "application/json"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini API error: " + response.statusCode() + " - " + response.body());
        }

        return response.body();
    }

    private AIResponse parseGeminiResponse(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        String aiText = root.path("candidates").get(0)
                .path("content").path("parts").get(0).path("text").asText();
        return AIResponseParser.parse(aiText, objectMapper);
    }

    // ── Streaming API call ──────────────────────────────────────────────

    /**
     * Calls Gemini streamGenerateContent endpoint with SSE.
     * Each SSE data line contains a JSON object with
     * candidates[0].content.parts[0].text.
     */
    private String streamGeminiAPI(String prompt, StreamChunkHandler chunkHandler) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":streamGenerateContent?alt=sse&key=" + apiKey;

        Map<String, Object> payload = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "responseMimeType", "application/json"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<Stream<String>> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofLines());

        if (response.statusCode() != 200) {
            String errorBody = String.join("\n", response.body().toList());
            throw new RuntimeException("Gemini streaming API error: " + response.statusCode() + " - " + errorBody);
        }

        StringBuilder accumulated = new StringBuilder();

        response.body().forEach(line -> {
            if (line.startsWith("data: ")) {
                String jsonData = line.substring(6).trim();
                if (jsonData.isEmpty())
                    return;
                try {
                    JsonNode chunk = objectMapper.readTree(jsonData);
                    JsonNode parts = chunk.path("candidates").path(0)
                            .path("content").path("parts");
                    if (parts.isArray() && parts.size() > 0) {
                        String text = parts.get(0).path("text").asText("");
                        if (!text.isEmpty()) {
                            accumulated.append(text);
                            chunkHandler.onChunk(text);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Skipping non-parseable SSE line: {}", jsonData);
                }
            }
        });

        return accumulated.toString();
    }
}

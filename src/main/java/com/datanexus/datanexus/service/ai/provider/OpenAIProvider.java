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
 * OpenAI AI provider implementation (non-streaming).
 * Uses the default streamChat() fallback from AIProvider interface.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAIProvider implements AIProvider {

    @Value("${ai.openai.api-key:}")
    private String apiKey;

    @Value("${ai.openai.model:gpt-4o}")
    private String model;

    @Value("${ai.openai.url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public String getName() {
        return "openai";
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
            throw new IllegalStateException("OpenAI provider is not configured. Please set ai.openai.api-key");
        }

        try {
            String prompt = resolvePrompt(request);
            boolean jsonMode = !request.isRawPrompt();
            String responseJson = callOpenAIAPI(prompt, jsonMode);

            if (request.isRawPrompt()) {
                // Raw prompt: return content directly without JSON parsing
                JsonNode root = objectMapper.readTree(responseJson);
                String content = root.path("choices").get(0).path("message").path("content").asText();
                return AIResponse.builder()
                        .type(AIResponseType.DIRECT_ANSWER)
                        .content(content)
                        .build();
            }

            return parseOpenAIResponse(responseJson);

        } catch (Exception e) {
            log.error("Failed to call OpenAI API: {}", e.getMessage(), e);
            return AIResponse.builder()
                    .type(AIResponseType.DIRECT_ANSWER)
                    .content("I encountered an error while processing your request: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public AIResponse streamChat(AIRequest request, StreamChunkHandler chunkHandler) {
        if (!isConfigured()) {
            throw new IllegalStateException("OpenAI provider is not configured. Please set ai.openai.api-key");
        }

        try {
            String prompt = resolvePrompt(request);
            String fullText = streamOpenAIAPI(prompt, chunkHandler);

            if (request.isRawPrompt()) {
                // Raw prompt: return content directly without JSON parsing
                return AIResponse.builder()
                        .type(AIResponseType.DIRECT_ANSWER)
                        .content(fullText)
                        .build();
            }

            return AIResponseParser.parse(fullText, objectMapper);

        } catch (Exception e) {
            log.error("Failed to stream OpenAI API: {}", e.getMessage(), e);
            return AIResponse.builder()
                    .type(AIResponseType.DIRECT_ANSWER)
                    .content("I encountered an error while processing your request: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Resolve the prompt text: raw user message for rawPrompt requests,
     * otherwise the full schema/decision-logic prompt from AIPromptBuilder.
     */
    private String resolvePrompt(AIRequest request) {
        if (request.isRawPrompt()) {
            // Use the pre-built prompt (analysis / dashboard prompt) — NOT the short userMessage
            return request.getPrompt() != null ? request.getPrompt() : request.getUserMessage();
        }
        return AIPromptBuilder.buildPrompt(request, true);
    }

    // ── Non-streaming API call ──────────────────────────────────────────

    private String callOpenAIAPI(String prompt, boolean jsonMode) throws Exception {
        String systemMsg = jsonMode
                ? "You are a data analyst assistant. Always respond with valid JSON."
                : "You are a data analyst assistant.";

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemMsg),
                Map.of("role", "user", "content", prompt)));
        payload.put("temperature", 0.2);
        if (jsonMode) {
            payload.put("response_format", Map.of("type", "json_object"));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("OpenAI API error: " + response.statusCode() + " - " + response.body());
        }

        return response.body();
    }

    private AIResponse parseOpenAIResponse(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        String aiText = root.path("choices").get(0).path("message").path("content").asText();
        return AIResponseParser.parse(aiText, objectMapper);
    }

    // ── Streaming API call ──────────────────────────────────────────────

    /**
     * Calls OpenAI Chat Completions API with stream:true.
     * SSE format: data: {"choices":[{"delta":{"content":"chunk"}}]}
     * Stream ends with: data: [DONE]
     */
    private String streamOpenAIAPI(String prompt, StreamChunkHandler chunkHandler) throws Exception {
        // Note: streaming doesn't support response_format, so we rely on the system
        // message to guide the model. For raw prompts, we drop the JSON instruction.
        String systemMsg = "You are a data analyst assistant.";

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("stream", true);
        payload.put("temperature", 0.2);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemMsg),
                Map.of("role", "user", "content", prompt)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<Stream<String>> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofLines());

        if (response.statusCode() != 200) {
            String errorBody = String.join("\n", response.body().toList());
            throw new RuntimeException("OpenAI streaming API error: " + response.statusCode() + " - " + errorBody);
        }

        StringBuilder accumulated = new StringBuilder();

        response.body().forEach(line -> {
            if (line.startsWith("data: ")) {
                String data = line.substring(6).trim();
                if (data.equals("[DONE]") || data.isEmpty())
                    return;
                try {
                    JsonNode chunk = objectMapper.readTree(data);
                    JsonNode delta = chunk.path("choices").path(0).path("delta");
                    String content = delta.path("content").asText("");
                    if (!content.isEmpty()) {
                        accumulated.append(content);
//                        chunkHandler.onChunk(content);
                    }
                } catch (Exception e) {
                    log.debug("Skipping non-parseable OpenAI SSE line: {}", data);
                }
            }
        });

        return accumulated.toString();
    }
}

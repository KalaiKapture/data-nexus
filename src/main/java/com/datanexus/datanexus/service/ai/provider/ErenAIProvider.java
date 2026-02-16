package com.datanexus.datanexus.service.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Eren AI provider implementation.
 * Integrates with external API at http://localhost:8080/api/chat.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ErenAIProvider implements AIProvider {

    private static final String API_URL = "http://localhost:8080/api/chat";
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public String getName() {
        return "eren";
    }

    @Override
    public boolean supportsClarification() {
        return false; // Assuming basic support for now
    }

    @Override
    public boolean isConfigured() {
        return true; // No key required
    }

    @Override
    public AIResponse chat(AIRequest request) {
        try {
            String prompt = AIPromptBuilder.buildPrompt(request,false);
            String responseJson = callErenAPI(prompt,request);
            return parseResponse(responseJson);
        } catch (Exception e) {
            log.error("Failed to call Eren API: {}", e.getMessage(), e);
            return AIResponse.builder()
                    .type(AIResponseType.DIRECT_ANSWER)
                    .content("I encountered an error while processing your request with Eren: " + e.getMessage())
                    .build();
        }
    }

    public AIResponse streamChat(AIRequest request, StreamChunkHandler chunkHandler) {
        return chat(request);
    }

    private String callErenAPI(String prompt,AIRequest request) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", request.getUserId());
        payload.put("conversationId", request.getConversationId());
        payload.put("message", prompt);
        payload.put("firstMessage", request.isFirstMessage());

        String jsonPayload = objectMapper.writeValueAsString(payload);
        log.debug("Sending to Eren API: {}", jsonPayload);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Eren API error: " + response.statusCode() + " - " + response.body());
        }

        return response.body();
    }

    private AIResponse parseResponse(String responseBody) throws Exception {
        try {
            // Try to parse as JSON first
            JsonNode root = objectMapper.readTree(responseBody);
            // If it's our standard AIResponse format
            if (root.has("type") && root.has("content")) {
                return objectMapper.treeToValue(root, AIResponse.class);
            }
            // If it has a 'content' field only
            if (root.has("content")) {
                return AIResponseParser.parse(root.get("content").asText(), objectMapper);
            }
            // Otherwise treat whole JSON as text
            return AIResponseParser.parse(responseBody, objectMapper);
        } catch (Exception e) {
            // Treat as plain text
            return AIResponseParser.parse(responseBody, objectMapper);
        }
    }
}

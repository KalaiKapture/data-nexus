package com.datanexus.datanexus.service.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONObject;
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

    private static final String API_URL = "http://localhost:8082/api/chat";
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
            String prompt = resolvePrompt(request);
            String responseJson = callErenAPI(prompt, request);

            if (request.isRawPrompt()) {
                // Raw prompt: return content directly without JSON parsing
                try {
                    String reply = JSONObject.fromObject(responseJson).optString("reply");
                    String content = (reply != null && !reply.isBlank()) ? reply : responseJson;
                    return AIResponse.builder()
                            .type(AIResponseType.DIRECT_ANSWER)
                            .content(content)
                            .build();
                } catch (Exception e) {
                    return AIResponse.builder()
                            .type(AIResponseType.DIRECT_ANSWER)
                            .content(responseJson)
                            .build();
                }
            }

            return parseResponse(responseJson);
        } catch (Exception e) {
            log.error("Failed to call Eren API: {}", e.getMessage(), e);
            return AIResponse.builder()
                    .type(AIResponseType.DIRECT_ANSWER)
                    .content("I encountered an error while processing your request with Eren: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public AIResponse streamChat(AIRequest request, StreamChunkHandler chunkHandler) {
        return chat(request);
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
        return AIPromptBuilder.buildPrompt(request, false);
    }

    private String callErenAPI(String prompt, AIRequest request) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", request.getUserId());
        payload.put("conversationId", request.getConversationId());
        payload.put("message", request.getUserMessage());
        payload.put("prompt", prompt);
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
            // Extract the 'reply' field which contains the actual AI response JSON
            String replyJson = JSONObject.fromObject(responseBody).optString("reply");

            if (replyJson != null && !replyJson.isBlank()) {
                return AIResponseParser.parse(replyJson, objectMapper);
            }

            // Fallback: treat the whole body as the response (legacy/simple mode)
            return AIResponseParser.parse(responseBody, objectMapper);
        } catch (Exception e) {
            log.error("Error parsing Eren API response: {}", e.getMessage());
            // Last resort fallback
            return AIResponseParser.parse(responseBody, objectMapper);
        }
    }
}

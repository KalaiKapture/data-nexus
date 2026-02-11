package com.datanexus.datanexus.service.ai.provider;

import com.datanexus.datanexus.service.datasource.request.SqlQuery;
import com.datanexus.datanexus.service.datasource.request.MCPToolCall;
import com.datanexus.datanexus.service.datasource.request.MCPResourceRead;
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

/**
 * Anthropic Claude AI provider implementation.
 * Includes schema + sample data in the prompt for accurate query generation.
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
            String prompt = buildPrompt(request);
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

    public String buildPrompt(AIRequest request) {
        return AIPromptBuilder.buildPrompt(request);
    }

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
        JsonNode textNode = root.path("content").get(0).path("text");

        String aiText = textNode.asText();
        JsonNode aiJson = objectMapper.readTree(aiText);

        AIResponseType type = AIResponseType.valueOf(aiJson.path("type").asText());
        String content = aiJson.path("content").asText();
        String intent = aiJson.path("intent").asText("");

        AIResponse.AIResponseBuilder builder = AIResponse.builder()
                .type(type)
                .content(content)
                .intent(intent);

        // Parse clarification if present
        if (type == AIResponseType.CLARIFICATION_NEEDED) {
            builder.clarificationQuestion(aiJson.path("clarificationQuestion").asText());
            List<String> options = new ArrayList<>();
            aiJson.path("suggestedOptions").forEach(node -> options.add(node.asText()));
            if (!options.isEmpty()) {
                builder.suggestedOptions(options);
            }
        }

        // Parse data requests if present
        if (type == AIResponseType.READY_TO_EXECUTE) {
            builder.dataRequests(parseDataRequests(aiJson.path("dataRequests")));
        }

        return builder.build();
    }

    private List<com.datanexus.datanexus.service.datasource.DataRequest> parseDataRequests(JsonNode requestsNode) {
        List<com.datanexus.datanexus.service.datasource.DataRequest> requests = new ArrayList<>();

        if (requestsNode.isArray()) {
            for (JsonNode reqNode : requestsNode) {
                String requestType = reqNode.path("requestType").asText();
                String explanation = reqNode.path("explanation").asText("");

                switch (requestType) {
                    case "SQL_QUERY" -> requests.add(SqlQuery.builder()
                            .sql(reqNode.path("sql").asText())
                            .explanation(explanation)
                            .build());

                    case "MCP_TOOL_CALL" -> {
                        Map<String, Object> args = new HashMap<>();
                        reqNode.path("arguments").fields()
                                .forEachRemaining(entry -> args.put(entry.getKey(), entry.getValue().asText()));

                        requests.add(MCPToolCall.builder()
                                .toolName(reqNode.path("toolName").asText())
                                .arguments(args)
                                .explanation(explanation)
                                .build());
                    }

                    case "MCP_RESOURCE_READ" -> requests.add(MCPResourceRead.builder()
                            .uri(reqNode.path("uri").asText())
                            .explanation(explanation)
                            .build());
                }
            }
        }

        return requests;
    }
}

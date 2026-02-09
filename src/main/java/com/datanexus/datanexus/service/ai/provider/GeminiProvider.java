package com.datanexus.datanexus.service.ai.provider;

import com.datanexus.datanexus.service.ai.SchemaService;
import com.datanexus.datanexus.service.datasource.DataSourceType;
import com.datanexus.datanexus.service.datasource.request.SqlQuery;
import com.datanexus.datanexus.service.datasource.request.MCPToolCall;
import com.datanexus.datanexus.service.datasource.request.MCPResourceRead;
import com.datanexus.datanexus.service.datasource.schema.MCPCapabilities;
import com.datanexus.datanexus.service.datasource.schema.SourceSchema;
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
 * Google Gemini AI provider implementation
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
            String prompt = buildPrompt(request);
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

    public String buildPrompt(AIRequest request) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are a data analyst assistant with access to multiple data sources.\n\n");

        sb.append("User Question: ").append(request.getUserMessage()).append("\n\n");

        sb.append("Available Data Sources:\n");
        for (SourceSchema schema : request.getAvailableSchemas()) {
            sb.append(formatSchema(schema)).append("\n\n");
        }

        sb.append("Your task:\n");
        sb.append("1. Analyze if you have enough information to answer the question\n");
        sb.append("2. If unclear, ask ONE specific clarification question\n");
        sb.append("3. If clear, generate appropriate requests for each relevant data source\n");
        sb.append("4. If you can answer directly without data, provide the answer\n\n");

        sb.append("Return JSON in this exact format:\n");
        sb.append("{\n");
        sb.append("  \"type\": \"CLARIFICATION_NEEDED\" | \"READY_TO_EXECUTE\" | \"DIRECT_ANSWER\",\n");
        sb.append("  \"content\": \"explanation or answer\",\n");
        sb.append("  \"intent\": \"brief description of user intent\",\n");
        sb.append("  \"clarificationQuestion\": \"optional question if type is CLARIFICATION_NEEDED\",\n");
        sb.append("  \"suggestedOptions\": [\"option1\", \"option2\"],\n");
        sb.append("  \"dataRequests\": [\n");
        sb.append("    {\n");
        sb.append("      \"sourceId\": \"connection_id\",\n");
        sb.append("      \"requestType\": \"SQL_QUERY\" | \"MCP_TOOL_CALL\" | \"MCP_RESOURCE_READ\",\n");
        sb.append("      \"sql\": \"for SQL requests\",\n");
        sb.append("      \"toolName\": \"for MCP tool calls\",\n");
        sb.append("      \"arguments\": {\"key\": \"value\"},\n");
        sb.append("      \"uri\": \"for MCP resource reads\",\n");
        sb.append("      \"explanation\": \"what this request does\"\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String formatSchema(SourceSchema schema) {
        StringBuilder sb = new StringBuilder();
        sb.append("Source: ").append(schema.getSourceName())
                .append(" (ID: ").append(schema.getSourceId())
                .append(", Type: ").append(schema.getSourceType()).append(")\n");

        if (schema.getSourceType() == DataSourceType.DATABASE) {
            SchemaService.DatabaseSchema dbSchema = (SchemaService.DatabaseSchema) schema.getSchemaData();
            sb.append("Database: ").append(dbSchema.getDatabaseType()).append("\n");
            sb.append("Tables:\n");
            for (SchemaService.TableSchema table : dbSchema.getTables()) {
                sb.append("  - ").append(table.getTableName()).append(" (");
                sb.append(String.join(", ", table.getColumns().stream()
                        .map(col -> col.getName() + ":" + col.getDataType())
                        .toList()));
                sb.append(")\n");
            }
        } else if (schema.getSourceType() == DataSourceType.MCP_SERVER) {
            MCPCapabilities mcp = (MCPCapabilities) schema.getSchemaData();
            sb.append("MCP Server Tools:\n");
            mcp.getTools().forEach(tool -> sb.append("  - ").append(tool.getName())
                    .append(": ").append(tool.getDescription()).append("\n"));
            sb.append("MCP Server Resources:\n");
            mcp.getResources().forEach(resource -> sb.append("  - ").append(resource.getName())
                    .append(" (").append(resource.getUri()).append(")\n"));
        }

        return sb.toString();
    }

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
        JsonNode textNode = root.path("candidates").get(0).path("content").path("parts").get(0).path("text");

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

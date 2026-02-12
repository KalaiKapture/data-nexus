package com.datanexus.datanexus.service.ai.provider;

import com.datanexus.datanexus.service.datasource.DataRequest;
import com.datanexus.datanexus.service.datasource.request.MCPResourceRead;
import com.datanexus.datanexus.service.datasource.request.MCPToolCall;
import com.datanexus.datanexus.service.datasource.request.SqlQuery;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared utility for parsing AI JSON responses into AIResponse objects.
 * Used by all providers to avoid duplicating the parsing logic.
 */
public class AIResponseParser {

    private AIResponseParser() {
    }

    /**
     * Parse a raw AI JSON text string into an AIResponse.
     * Handles markdown code blocks, extraneous text around JSON,
     * and missing/invalid type fields gracefully.
     */
    public static AIResponse parse(String aiText, ObjectMapper objectMapper) throws Exception {
        String cleanText = stripMarkdownAndExtractJson(aiText);
        JsonNode aiJson = objectMapper.readTree(cleanText);

        // Gracefully handle missing or invalid type
        String typeStr = aiJson.path("type").asText("").trim();
        AIResponseType type;
        try {
            type = AIResponseType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            // If type is empty or invalid, treat as DIRECT_ANSWER
            type = AIResponseType.DIRECT_ANSWER;
        }

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

    private static List<DataRequest> parseDataRequests(JsonNode requestsNode) {
        List<DataRequest> requests = new ArrayList<>();

        if (requestsNode.isArray()) {
            for (JsonNode reqNode : requestsNode) {
                String requestType = reqNode.path("requestType").asText();
                String explanation = reqNode.path("explanation").asText("");

                // Chaining fields (optional)
                String sourceId = reqNode.path("sourceId").asText(null);
                Integer step = reqNode.has("step") ? reqNode.path("step").asInt() : null;
                Integer dependsOn = reqNode.has("dependsOn") ? reqNode.path("dependsOn").asInt() : null;
                String outputAs = reqNode.path("outputAs").asText(null);
                String outputField = reqNode.path("outputField").asText(null);

                switch (requestType) {
                    case "SQL_QUERY" -> requests.add(SqlQuery.builder()
                            .sql(reqNode.path("sql").asText())
                            .explanation(explanation)
                            .sourceId(sourceId)
                            .step(step)
                            .dependsOn(dependsOn)
                            .outputAs(outputAs)
                            .outputField(outputField)
                            .build());

                    case "MCP_TOOL_CALL" -> {
                        Map<String, Object> args = new HashMap<>();
                        reqNode.path("arguments").fields()
                                .forEachRemaining(entry -> args.put(entry.getKey(), entry.getValue().asText()));

                        requests.add(MCPToolCall.builder()
                                .toolName(reqNode.path("toolName").asText())
                                .arguments(args)
                                .explanation(explanation)
                                .sourceId(sourceId)
                                .step(step)
                                .dependsOn(dependsOn)
                                .outputAs(outputAs)
                                .outputField(outputField)
                                .build());
                    }

                    case "MCP_RESOURCE_READ" -> requests.add(MCPResourceRead.builder()
                            .uri(reqNode.path("uri").asText())
                            .explanation(explanation)
                            .sourceId(sourceId)
                            .step(step)
                            .dependsOn(dependsOn)
                            .outputAs(outputAs)
                            .outputField(outputField)
                            .build());
                }
            }
        }

        return requests;
    }

    /**
     * Strip markdown code blocks and extract JSON from AI text.
     * Handles cases like: ```json\n{...}\n``` or text before/after JSON.
     */
    private static String stripMarkdownAndExtractJson(String text) {
        if (text == null || text.isBlank()) {
            return "{}";
        }

        String cleaned = text.trim();

        // Strip markdown code blocks: ```json ... ``` or ``` ... ```
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline > 0) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();
        }

        // If still not starting with {, try to extract JSON object
        if (!cleaned.startsWith("{")) {
            int jsonStart = cleaned.indexOf('{');
            int jsonEnd = cleaned.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                cleaned = cleaned.substring(jsonStart, jsonEnd + 1);
            }
        }

        return cleaned;
    }
}

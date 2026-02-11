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
     * The text is expected to be the structured JSON the AI was instructed to
     * return.
     */
    public static AIResponse parse(String aiText, ObjectMapper objectMapper) throws Exception {
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

    private static List<DataRequest> parseDataRequests(JsonNode requestsNode) {
        List<DataRequest> requests = new ArrayList<>();

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

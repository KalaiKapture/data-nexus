package com.datanexus.datanexus.service.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that verify the SSE response structure parsing for all AI providers
 * and the shared AIResponseParser.
 *
 * These tests simulate the exact JSON structures returned by each provider's
 * streaming API, so we can validate parsing without making real API calls.
 */
class StreamingResponseParsingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ═══════════════════════════════════════════════════════════════════
    // 1. AIResponseParser — parses the final accumulated AI JSON
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("AIResponseParser: READY_TO_EXECUTE with SQL query")
    void parseReadyToExecute() throws Exception {
        String aiJson = """
                {
                  "type": "READY_TO_EXECUTE",
                  "content": "I'll query the orders table to get monthly revenue.",
                  "intent": "monthly revenue analysis",
                  "dataRequests": [
                    {
                      "sourceId": "42",
                      "requestType": "SQL_QUERY",
                      "sql": "SELECT DATE_TRUNC('month', order_date) AS month, SUM(total) AS revenue FROM orders GROUP BY month ORDER BY month",
                      "explanation": "Aggregates revenue by month from orders table"
                    }
                  ]
                }
                """;

        AIResponse response = AIResponseParser.parse(aiJson, objectMapper);

        assertEquals(AIResponseType.READY_TO_EXECUTE, response.getType());
        assertEquals("I'll query the orders table to get monthly revenue.", response.getContent());
        assertEquals("monthly revenue analysis", response.getIntent());
        assertNotNull(response.getDataRequests());
        assertEquals(1, response.getDataRequests().size());

        // Verify the SQL query was extracted correctly
        var dataReq = response.getDataRequests().get(0);
        assertTrue(dataReq instanceof com.datanexus.datanexus.service.datasource.request.SqlQuery);
        var sqlReq = (com.datanexus.datanexus.service.datasource.request.SqlQuery) dataReq;
        assertTrue(sqlReq.getSql().contains("DATE_TRUNC"));
        assertEquals("Aggregates revenue by month from orders table", sqlReq.getExplanation());

        System.out.println("✅ READY_TO_EXECUTE parsed successfully:");
        System.out.println("   Type: " + response.getType());
        System.out.println("   Content: " + response.getContent());
        System.out.println("   SQL: " + sqlReq.getSql());
    }

    @Test
    @DisplayName("AIResponseParser: CLARIFICATION_NEEDED with options")
    void parseClarificationNeeded() throws Exception {
        String aiJson = """
                {
                  "type": "CLARIFICATION_NEEDED",
                  "content": "I found multiple date columns. Which one should I use?",
                  "intent": "date column selection",
                  "clarificationQuestion": "Which date column would you like to analyze: order_date, ship_date, or created_at?",
                  "suggestedOptions": ["order_date", "ship_date", "created_at"]
                }
                """;

        AIResponse response = AIResponseParser.parse(aiJson, objectMapper);

        assertEquals(AIResponseType.CLARIFICATION_NEEDED, response.getType());
        assertEquals("Which date column would you like to analyze: order_date, ship_date, or created_at?",
                response.getClarificationQuestion());
        assertEquals(3, response.getSuggestedOptions().size());
        assertTrue(response.getSuggestedOptions().contains("order_date"));

        System.out.println("✅ CLARIFICATION_NEEDED parsed successfully:");
        System.out.println("   Question: " + response.getClarificationQuestion());
        System.out.println("   Options: " + response.getSuggestedOptions());
    }

    @Test
    @DisplayName("AIResponseParser: DIRECT_ANSWER")
    void parseDirectAnswer() throws Exception {
        String aiJson = """
                {
                  "type": "DIRECT_ANSWER",
                  "content": "SQL databases use B-tree indexes by default. They speed up lookups but slow down writes.",
                  "intent": "general SQL knowledge"
                }
                """;

        AIResponse response = AIResponseParser.parse(aiJson, objectMapper);

        assertEquals(AIResponseType.DIRECT_ANSWER, response.getType());
        assertTrue(response.getContent().contains("B-tree"));
        assertNull(response.getDataRequests());

        System.out.println("✅ DIRECT_ANSWER parsed successfully:");
        System.out.println("   Content: " + response.getContent());
    }

    // ═══════════════════════════════════════════════════════════════════
    // 2. Gemini SSE chunk parsing
    // Format: data: {"candidates":[{"content":{"parts":[{"text":"chunk"}]}}]}
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Gemini: parse SSE data lines and accumulate text")
    void geminiSSEParsing() throws Exception {
        // Simulated SSE lines from Gemini streamGenerateContent
        List<String> sseLines = List.of(
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\n  \\\"type\\\": \\\"READY\"}]}}]}",
                "",
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"_TO_EXECUTE\\\",\\n  \\\"content\\\": \\\"Querying\"}]}}]}",
                "",
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\" orders table\\\",\\n  \\\"intent\\\": \\\"revenue\\\",\\n  \\\"dataRequests\\\": [{\\\"sourceId\\\": \\\"1\\\", \\\"requestType\\\": \\\"SQL_QUERY\\\", \\\"sql\\\": \\\"SELECT * FROM orders\\\", \\\"explanation\\\": \\\"Get orders\\\"}]\\n}\"}]}}]}",
                "");

        // Simulate parsing like GeminiProvider does
        StringBuilder accumulated = new StringBuilder();
        List<String> chunks = new ArrayList<>();

        for (String line : sseLines) {
            if (line.startsWith("data: ")) {
                String jsonData = line.substring(6).trim();
                if (jsonData.isEmpty())
                    continue;
                var chunk = objectMapper.readTree(jsonData);
                var parts = chunk.path("candidates").path(0).path("content").path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    String text = parts.get(0).path("text").asText("");
                    if (!text.isEmpty()) {
                        accumulated.append(text);
                        chunks.add(text);
                    }
                }
            }
        }

        String fullText = accumulated.toString();
        AIResponse response = AIResponseParser.parse(fullText, objectMapper);

        assertEquals(AIResponseType.READY_TO_EXECUTE, response.getType());
        assertEquals("Querying orders table", response.getContent());
        assertEquals(3, chunks.size());

        System.out.println("✅ Gemini SSE parsed successfully:");
        System.out.println("   Chunks received: " + chunks.size());
        System.out.println("   Chunk 1: " + chunks.get(0));
        System.out.println("   Chunk 2: " + chunks.get(1));
        System.out.println("   Chunk 3: " + chunks.get(2));
        System.out.println("   Full accumulated text: " + fullText);
        System.out.println("   Parsed type: " + response.getType());
        System.out.println("   Parsed SQL: "
                + ((com.datanexus.datanexus.service.datasource.request.SqlQuery) response.getDataRequests().get(0))
                        .getSql());
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3. Claude SSE chunk parsing
    // Format: event: content_block_delta
    // data:
    // {"type":"content_block_delta","delta":{"type":"text_delta","text":"chunk"}}
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Claude: parse SSE content_block_delta events")
    void claudeSSEParsing() throws Exception {
        // Simulated SSE lines from Claude Messages API with stream:true
        List<String> sseLines = List.of(
                "event: message_start",
                "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_123\",\"type\":\"message\",\"role\":\"assistant\"}}",
                "",
                "event: content_block_start",
                "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}",
                "",
                "event: content_block_delta",
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"{\\n  \\\"type\\\": \\\"DIRECT_ANSWER\\\",\"}}",
                "",
                "event: content_block_delta",
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"\\n  \\\"content\\\": \\\"The average order value is calculated by dividing total revenue by number of orders.\\\",\"}}",
                "",
                "event: content_block_delta",
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"\\n  \\\"intent\\\": \\\"explain AOV\\\"\\n}\"}}",
                "",
                "event: content_block_stop",
                "data: {\"type\":\"content_block_stop\",\"index\":0}",
                "",
                "event: message_stop",
                "data: {\"type\":\"message_stop\"}");

        // Simulate parsing like ClaudeProvider does
        StringBuilder accumulated = new StringBuilder();
        List<String> chunks = new ArrayList<>();

        for (String line : sseLines) {
            if (line.startsWith("data: ")) {
                String jsonData = line.substring(6).trim();
                if (jsonData.isEmpty())
                    continue;
                var event = objectMapper.readTree(jsonData);
                String eventType = event.path("type").asText("");

                if ("content_block_delta".equals(eventType)) {
                    String text = event.path("delta").path("text").asText("");
                    if (!text.isEmpty()) {
                        accumulated.append(text);
                        chunks.add(text);
                    }
                }
            }
        }

        String fullText = accumulated.toString();
        AIResponse response = AIResponseParser.parse(fullText, objectMapper);

        assertEquals(AIResponseType.DIRECT_ANSWER, response.getType());
        assertTrue(response.getContent().contains("average order value"));
        assertEquals(3, chunks.size());

        System.out.println("✅ Claude SSE parsed successfully:");
        System.out.println("   Chunks received: " + chunks.size());
        System.out.println("   Chunk 1: " + chunks.get(0));
        System.out.println("   Chunk 2: " + chunks.get(1));
        System.out.println("   Chunk 3: " + chunks.get(2));
        System.out.println("   Full accumulated text: " + fullText);
        System.out.println("   Parsed type: " + response.getType());
        System.out.println("   Parsed content: " + response.getContent());
    }

    // ═══════════════════════════════════════════════════════════════════
    // 4. OpenAI SSE chunk parsing
    // Format: data: {"choices":[{"delta":{"content":"chunk"}}]}
    // data: [DONE]
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("OpenAI: parse SSE delta content lines")
    void openAISSEParsing() throws Exception {
        // Simulated SSE lines from OpenAI Chat Completions with stream:true
        List<String> sseLines = List.of(
                "data: {\"id\":\"chatcmpl-abc\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"\"}}]}",
                "",
                "data: {\"id\":\"chatcmpl-abc\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"{\\n  \\\"type\\\": \\\"READY_TO_EXECUTE\\\",\"}}]}",
                "",
                "data: {\"id\":\"chatcmpl-abc\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"\\n  \\\"content\\\": \\\"Fetching top 10 customers by revenue\\\",\"}}]}",
                "",
                "data: {\"id\":\"chatcmpl-abc\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"\\n  \\\"intent\\\": \\\"top customers\\\",\\n  \\\"dataRequests\\\": [{\\\"sourceId\\\": \\\"5\\\", \\\"requestType\\\": \\\"SQL_QUERY\\\", \\\"sql\\\": \\\"SELECT customer_name, SUM(total) AS revenue FROM orders GROUP BY customer_name ORDER BY revenue DESC LIMIT 10\\\", \\\"explanation\\\": \\\"Top 10 customers\\\"}]\\n}\"}}]}",
                "",
                "data: [DONE]");

        // Simulate parsing like OpenAIProvider does
        StringBuilder accumulated = new StringBuilder();
        List<String> chunks = new ArrayList<>();

        for (String line : sseLines) {
            if (line.startsWith("data: ")) {
                String data = line.substring(6).trim();
                if (data.equals("[DONE]") || data.isEmpty())
                    continue;
                var chunk = objectMapper.readTree(data);
                var delta = chunk.path("choices").path(0).path("delta");
                String content = delta.path("content").asText("");
                if (!content.isEmpty()) {
                    accumulated.append(content);
                    chunks.add(content);
                }
            }
        }

        String fullText = accumulated.toString();
        AIResponse response = AIResponseParser.parse(fullText, objectMapper);

        assertEquals(AIResponseType.READY_TO_EXECUTE, response.getType());
        assertEquals("Fetching top 10 customers by revenue", response.getContent());
        assertEquals(1, response.getDataRequests().size());

        var sqlReq = (com.datanexus.datanexus.service.datasource.request.SqlQuery) response.getDataRequests().get(0);
        assertTrue(sqlReq.getSql().contains("LIMIT 10"));

        System.out.println("✅ OpenAI SSE parsed successfully:");
        System.out.println("   Chunks received: " + chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            System.out.println("   Chunk " + (i + 1) + ": " + chunks.get(i));
        }
        System.out.println("   Full accumulated text: " + fullText);
        System.out.println("   Parsed type: " + response.getType());
        System.out.println("   Parsed SQL: " + sqlReq.getSql());
    }

    // ═══════════════════════════════════════════════════════════════════
    // 5. Multiple data requests (multi-source)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("AIResponseParser: Multiple SQL queries across sources")
    void parseMultipleDataRequests() throws Exception {
        String aiJson = """
                {
                  "type": "READY_TO_EXECUTE",
                  "content": "I'll query both the orders and inventory databases.",
                  "intent": "cross-source comparison",
                  "dataRequests": [
                    {
                      "sourceId": "1",
                      "requestType": "SQL_QUERY",
                      "sql": "SELECT product_id, SUM(quantity) AS sold FROM orders GROUP BY product_id",
                      "explanation": "Total sold per product from orders DB"
                    },
                    {
                      "sourceId": "2",
                      "requestType": "SQL_QUERY",
                      "sql": "SELECT product_id, stock_quantity FROM inventory",
                      "explanation": "Current stock from inventory DB"
                    }
                  ]
                }
                """;

        AIResponse response = AIResponseParser.parse(aiJson, objectMapper);

        assertEquals(AIResponseType.READY_TO_EXECUTE, response.getType());
        assertEquals(2, response.getDataRequests().size());

        System.out.println("✅ Multi-source queries parsed successfully:");
        System.out.println("   Data requests: " + response.getDataRequests().size());
        for (int i = 0; i < response.getDataRequests().size(); i++) {
            var req = (com.datanexus.datanexus.service.datasource.request.SqlQuery) response.getDataRequests().get(i);
            System.out.println("   [" + (i + 1) + "] SQL: " + req.getSql());
            System.out.println("       Explanation: " + req.getExplanation());
        }
    }
}

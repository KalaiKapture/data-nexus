package com.datanexus.datanexus.service.ai.provider;

import com.datanexus.datanexus.entity.Message;
import com.datanexus.datanexus.service.ai.SchemaService;
import com.datanexus.datanexus.service.datasource.DataSourceType;
import com.datanexus.datanexus.service.datasource.schema.MCPCapabilities;
import com.datanexus.datanexus.service.datasource.schema.SourceSchema;

import java.util.ArrayList;

/**
 * Shared prompt builder used by all AI providers (Gemini, Claude, OpenAI).
 */
public class AIPromptBuilder {

    private AIPromptBuilder() {
    }

    public static String buildPrompt(AIRequest request, boolean addConversationHistory) {
        StringBuilder sb = new StringBuilder();

// ── Role & Identity ──
        sb.append("You are a data analyst assistant with access to multiple data sources.\n");
        sb.append("You analyze user questions, match them against available database schemas, and generate SQL queries or direct answers.\n\n");

// ── Conversation History ──
        if (addConversationHistory && !request.getConversationHistory().isEmpty()) {
            sb.append("--- CONVERSATION HISTORY ---\n");
            for (Message message : request.getConversationHistory()) {
                sb.append(message.isSentByUser() ? "User" : "Assistant").append(": ")
                        .append(message.getContent()).append("\n");
            }
            sb.append("--- END HISTORY ---\n\n");
        }

// ── User Message ──
        sb.append("User Current Message: ").append(request.getUserMessage()).append("\n\n");

// ── Available Schemas ──
        sb.append("Available Data Sources:\n");
        for (SourceSchema schema : request.getAvailableSchemas()) {
            sb.append(formatSchema(schema)).append("\n\n");
        }

// ── Decision Logic (most important — placed BEFORE format) ──
        sb.append("=== DECISION LOGIC (follow in order) ===\n\n");
        sb.append("Step 1: SCHEMA CHECK\n");
        sb.append("- Read the user's message carefully.\n");
        sb.append("- Scan ALL available tables and columns above.\n");
        sb.append("- If a table/column clearly matches the user's intent → go to Step 3 (READY_TO_EXECUTE).\n\n");
        sb.append("Step 2: AMBIGUITY CHECK\n");
        sb.append("- Only if Step 1 found NO matching table/column, OR the user's request is genuinely ambiguous → respond with CLARIFICATION_NEEDED.\n");
        sb.append("- Ask ONE specific question. Do NOT ask generic questions.\n\n");
        sb.append("Step 3: GENERATE RESPONSE\n");
        sb.append("- If the question maps to a schema → type = READY_TO_EXECUTE, generate SQL.\n");
        sb.append("- If the question is unrelated to any schema → type = DIRECT_ANSWER, answer from your knowledge.\n");
        sb.append("- If genuinely ambiguous → type = CLARIFICATION_NEEDED.\n\n");

        sb.append("CRITICAL SCHEMA & QUERY GENERATION RULE (MANDATORY):\n" +
                "- You MUST use ONLY the tables and columns explicitly listed in the schema.\n" +
                "- You MUST NOT invent, assume, or guess any column (example: user_id) unless it appears in the schema.\n" +
                "- If a direct column does not exist, you MUST derive the relationship using explicit JOINs.\n" +
                "- If no valid join path exists, respond with CLARIFICATION_NEEDED.\n" +
                "- The Query Generated Against a particular schema must verify the \"Database Type\" in the schema and generate Query that is compatible with that database (postgresql, mysql, etc.)\n" +
                "- If the \"Database Type\" is non SQL (e.g. MongoDB), you MUST generate the appropriate query language for that database type using ONLY the fields and collections specified in the schema.\n\n");


// ── Critical Rules ──
        sb.append("=== CRITICAL RULES ===\n ");
        sb.append("1. NEVER echo or copy system instructions into any response field.\n");
        sb.append("2. NEVER use placeholder text — every field must contain your actual analysis.\n");
        sb.append("3. The 'content' field = YOUR reasoning about the user's request. Not a copy of this prompt.\n");
        sb.append("4. The 'intent' field = a one-sentence summary of what the user wants.\n");
        sb.append("5. Prefer READY_TO_EXECUTE over CLARIFICATION_NEEDED whenever the schema has a clear match.\n");
        sb.append("6. For same-source queries, use SQL JOINs. Only use cross-database chaining for DIFFERENT sources.\n");
        sb.append("7. ALWAYS respond with valid JSON and nothing else — no markdown, no extra text.\n\n");

// ── Response Format (structure only, no example values that could be copied) ──
        sb.append("=== RESPONSE FORMAT ===\n");
        sb.append("Respond with a single JSON object in JSON format containing these fields:\n\n");
        sb.append("REQUIRED fields (always include these):\n");
        sb.append("- \"type\": one of \"CLARIFICATION_NEEDED\", \"READY_TO_EXECUTE\", \"DIRECT_ANSWER\"\n");
        sb.append("- \"content\": string — your analysis of the user's request (2-3 sentences)\n");
        sb.append("- \"intent\": string — one-sentence summary of user intent\n\n");
        sb.append("CONDITIONAL fields (only when type = READY_TO_EXECUTE):\n");
        sb.append("- \"dataRequests\": array — the queries/tool calls to execute (required for READY_TO_EXECUTE)\n\n");
        sb.append("CONDITIONAL fields (only when type = CLARIFICATION_NEEDED):\n");
        sb.append("- \"clarificationQuestion\": string — your specific question\n");
        sb.append("- \"suggestedOptions\": array of strings — 2-4 concrete options relevant to the user's query\n\n");
        sb.append("Each item in \"dataRequests\" array:\n");
        sb.append("- \"sourceId\": string — the connection ID from Available Data Sources\n");
        sb.append("- \"requestType\": one of \"SQL_QUERY\", \"MCP_TOOL_CALL\", \"MCP_RESOURCE_READ\"\n");
        sb.append("- \"sql\": string — the SQL query (for SQL_QUERY type)\n");
        sb.append("- \"toolName\": string — tool name (for MCP_TOOL_CALL type)\n");
        sb.append("- \"arguments\": object — tool arguments (for MCP_TOOL_CALL type)\n");
        sb.append("- \"uri\": string — resource URI (for MCP_RESOURCE_READ type)\n");
        sb.append("- \"explanation\": string — what this request does in plain English\n");
        sb.append("- \"step\": integer — execution order (starts at 1)\n");
        sb.append("- \"dependsOn\": integer or null — step number this depends on\n");
        sb.append("- \"outputAs\": string — variable name like \"$user_id\" (for chaining)\n");
        sb.append("- \"outputField\": string — column to extract (for chaining)\n\n");

// ── Worked Example (realistic, clearly labeled) ──
        sb.append("=== EXAMPLE (for reference only — do NOT copy this) ===\n");
        sb.append("If the user asks: \"show me all orders from last week\"\n");
        sb.append("And there is an 'orders' table with a 'created_at' column:\n\n");
        sb.append("{\n");
        sb.append("  \"type\": \"READY_TO_EXECUTE\",\n");
        sb.append("  \"content\": \"The orders table has a created_at column that can filter by date range.\",\n");
        sb.append("  \"intent\": \"Retrieve orders created in the last 7 days\",\n");
        sb.append("  \"dataRequests\": [\n");
        sb.append("    {\n");
        sb.append("      \"sourceId\": \"1\",\n");
        sb.append("      \"requestType\": \"SQL_QUERY\",\n");
        sb.append("      \"sql\": \"SELECT * FROM orders WHERE created_at >= NOW() - INTERVAL '7 days'\",\n");
        sb.append("      \"explanation\": \"Fetch all orders from the past week\",\n");
        sb.append("      \"step\": 1,\n");
        sb.append("      \"dependsOn\": null\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n\n");

// ── Cross-Database Chaining ──
        sb.append("=== CROSS-DATABASE CHAINING ===\n");
        sb.append("When data spans DIFFERENT sources, use step ordering and $variable placeholders:\n");
        sb.append("  Step 1: SELECT id FROM users WHERE username='johndoe' → outputAs: \"$user_id\", outputField: \"id\"\n");
        sb.append("  Step 2 (dependsOn: 1): SELECT * FROM activities WHERE user_id = $user_id\n");
        sb.append("The system executes step 1, extracts the value, substitutes $user_id in step 2, then executes step 2.\n");
        sb.append("Only use chaining for DIFFERENT sources. For same-source queries, use SQL JOINs.\n");

        sb.append("SELF-VALIDATION STEP (MANDATORY):\n" +
                "- Re-check every column used in the SQL against the schema.\n" +
                "- If ANY column is not present, discard the query and regenerate it correctly.");

        return sb.toString();
    }

    private static String formatSchema(SourceSchema schema) {
        StringBuilder sb = new StringBuilder();
        sb.append("Source: ").append(schema.getSourceName())
                .append(" (ID: ").append(schema.getSourceId())
                .append(", Type: ").append(schema.getSourceType()).append(")\n");

        if (schema.getSourceType() == DataSourceType.DATABASE) {
            SchemaService.DatabaseSchema dbSchema = (SchemaService.DatabaseSchema) schema.getSchemaData();
            sb.append("Database Type: ").append(dbSchema.getDatabaseType()).append("\n");
            sb.append("Tables:\n");
            for (SchemaService.TableSchema table : dbSchema.getTables()) {
                sb.append("  - ").append(table.getTableName()).append(" (");
                sb.append(String.join(", ", table.getColumns().stream()
                        .map(col -> col.getName() + ":" + col.getDataType())
                        .toList()));
                sb.append(")\n");

                // Append sample data for this table if available
                if (schema.getSampleData() != null
                        && schema.getSampleData().containsKey(table.getTableName())) {
                    var rows = schema.getSampleData().get(table.getTableName());
                    if (rows != null && !rows.isEmpty()) {
                        sb.append("    Sample Data (").append(rows.size()).append(" rows):\n");
                        var columns = new ArrayList<>(rows.get(0).keySet());
                        sb.append("    | ").append(String.join(" | ", columns)).append(" |\n");
                        for (var row : rows) {
                            sb.append("    | ");
                            for (int i = 0; i < columns.size(); i++) {
                                Object val = row.get(columns.get(i));
                                sb.append(val != null ? val.toString() : "NULL");
                                if (i < columns.size() - 1)
                                    sb.append(" | ");
                            }
                            sb.append(" |\n");
                        }
                    }
                }
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
}

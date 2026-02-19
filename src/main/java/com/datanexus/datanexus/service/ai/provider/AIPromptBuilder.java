package com.datanexus.datanexus.service.ai.provider;

import com.datanexus.datanexus.entity.Message;
import com.datanexus.datanexus.service.ai.SchemaService;
import com.datanexus.datanexus.service.datasource.DataSourceType;
import com.datanexus.datanexus.service.datasource.schema.MCPCapabilities;
import com.datanexus.datanexus.service.datasource.schema.SourceSchema;

import com.datanexus.datanexus.dto.websocket.AnalyzeResponse;
import com.datanexus.datanexus.service.ai.DataSummarizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared prompt builder used by all AI providers (Gemini, Claude, OpenAI).
 */
@Slf4j
public class AIPromptBuilder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    // ═══════════════════════════════════════════════════════════════════════
    // Phase 2: Data Analysis Prompt (after query execution)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Build a prompt that asks the AI to analyze the raw query result data.
     * The AI returns a JSON with analysis text.
     */
    public static String buildAnalysisPrompt(String userQuestion,
                                             List<AnalyzeResponse.QueryResult> queryResults) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are a senior data analyst. Queries were executed and you have the structural profile of the results below.\n");
        sb.append("Your job: Produce a meaningful, business-focused analysis based on what the data represents.\n\n");

        sb.append("=== USER QUESTION ===\n");
        sb.append(userQuestion).append("\n\n");

        // Structural summary — sensitive columns already redacted by DataSummarizer
        sb.append("=== DATA STRUCTURE & STATISTICS ===\n");
        sb.append("NOTE: Columns marked [REDACTED] are sensitive (passwords, emails, tokens etc.) — do NOT mention or analyse their values.\n\n");
        sb.append(DataSummarizer.buildStructureSummary(queryResults));

        sb.append("=== ANALYSIS RULES ===\n");
        sb.append("1. FOCUS on business-meaningful columns: numeric metrics, dates, status/category fields, counts.\n");
        sb.append("2. IGNORE / DO NOT MENTION columns that are: passwords, hashes, tokens, raw email addresses, internal IDs with no analytical meaning.\n");
        sb.append("3. If the data is a simple lookup/config/auth table (e.g. users with only id+email+password), say so honestly:\n");
        sb.append("   set analysis to 'This result set is a system/configuration table and does not contain analytical metrics.'\n");
        sb.append("   and set keyMetrics to just Total Rows, chartSuggestions to [].\n");
        sb.append("4. For genuinely analytical data (orders, sales, events, logs, metrics): provide rich insights.\n");
        sb.append("5. Key metrics must use REAL values from the statistics above — not placeholder text.\n");
        sb.append("6. Chart suggestions only if there are meaningful numeric+categorical/date column pairs to plot.\n");
        sb.append("7. Use the correct icon emoji for each metric:\n");
        sb.append("   count/total → 📊, revenue/amount → 💰, average → 📈, date/time → 📅, users → 👤, orders → 🛒, status → 🔵\n\n");

        sb.append("=== RESPONSE FORMAT ===\n");
        sb.append("Respond with a single valid JSON object — no markdown fences, no extra text:\n");
        sb.append("{\n");
        sb.append("  \"title\": \"Short dashboard title based on what the data actually represents (e.g. 'Orders Analytics', 'Revenue by Region')\",\n");
        sb.append("  \"analysis\": \"Business-focused analysis in markdown. Use ## headers, **bold** for key numbers, - bullet points. Reference actual statistics. Skip sensitive columns entirely.\",\n");
        sb.append("  \"keyMetrics\": [\n");
        sb.append("    { \"label\": \"Total Rows\", \"value\": \"<actual rowCount>\", \"icon\": \"📊\" },\n");
        sb.append("    { \"label\": \"<metric from data>\", \"value\": \"<actual computed value>\", \"icon\": \"<emoji>\" }\n");
        sb.append("  ],\n");
        sb.append("  \"chartSuggestions\": [\n");
        sb.append("    { \"type\": \"bar|line|pie|doughnut\", \"title\": \"<chart title>\", \"xField\": \"<column_name>\", \"yField\": \"<column_name>\", \"datasetIndex\": 0 }\n");
        sb.append("  ]\n");
        sb.append("}\n");

        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Phase 3: Dashboard Chart-Config Prompt  (server owns the HTML skeleton)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Ask the AI for a rich dashboard configuration JSON.
     * The server owns all HTML/CSS/JS and renders the page from this config.
     * The AI specifies: which columns to chart, what aggregation to use,
     * metric card values, and a visual theme — nothing else.
     */
    public static String buildDashboardPrompt(String title,
                                              String analysis,
                                              String keyMetricsJson,
                                              String chartSuggestionsJson,
                                              List<AnalyzeResponse.QueryResult> queryResults) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are a senior data analyst. Your job: produce a dashboard configuration JSON.\n");
        sb.append("The server will render the full HTML from your config — do NOT write any HTML, CSS, or JavaScript.\n\n");

        sb.append("=== DASHBOARD TITLE ===\n").append(title).append("\n\n");
        sb.append("=== PREVIOUS ANALYSIS ===\n").append(analysis).append("\n\n");
        sb.append("=== KEY METRICS (from analysis phase) ===\n").append(keyMetricsJson).append("\n\n");
        sb.append("=== CHART SUGGESTIONS (from analysis phase) ===\n").append(chartSuggestionsJson).append("\n\n");

        sb.append("=== DATA STRUCTURE & STATISTICS ===\n");
        sb.append("IMPORTANT: Use ONLY exact column names listed here. isNumeric=true means the column has real numbers.\n\n");
        sb.append(DataSummarizer.buildStructureSummary(queryResults));

        sb.append("=== CHART CONFIGURATION RULES ===\n");
        sb.append("You must specify as many meaningful charts as the data supports (up to 5). Be creative — vary the chart types.\n\n");

        sb.append("Allowed chart types and their required fields:\n");
        sb.append("  \"bar\"      → xField (string/date col), yField (numeric col), optional: aggregation (\"sum\"|\"avg\"|\"count\"|\"max\"|\"min\")\n");
        sb.append("  \"line\"     → xField (date/ordered col), yField (numeric col), optional: aggregation\n");
        sb.append("  \"pie\"      → labelField (string/category col), valueField (numeric col), optional: aggregation\n");
        sb.append("  \"doughnut\" → labelField (string/category col), valueField (numeric col), optional: aggregation\n");
        sb.append("  \"scatter\"  → xField (numeric col), yField (numeric col) — no aggregation\n\n");

        sb.append("CRITICAL chart rules:\n");
        sb.append("  - yField / valueField MUST be a column where isNumeric=true. Never use a string column as a value.\n");
        sb.append("  - xField for bar/line should be a low-cardinality string or date (not an ID with thousands of unique values).\n");
        sb.append("  - labelField for pie/doughnut: max ~10 distinct values is ideal.\n");
        sb.append("  - aggregation: use 'count' when you want to count how many rows share an xField value.\n");
        sb.append("    Use 'sum'/'avg' when yField is a real measure (price, quantity, duration, score).\n");
        sb.append("  - If only one numeric column exists, you may use aggregation:'count' with a string xField.\n");
        sb.append("  - No charts if the data is purely a config/auth table (only id/email/password columns).\n\n");

        sb.append("=== METRIC CARD RULES ===\n");
        sb.append("  - Always include 'Total Records' as first metric with the actual rowCount.\n");
        sb.append("  - Add 2–4 more metrics using REAL numbers from the statistics (min, max, avg, sum, distinct counts).\n");
        sb.append("  - Never use empty string or null for value.\n");
        sb.append("  - Icon options: 📊 count/total, 💰 revenue/amount, 📈 average/growth, 📅 date/time, 👤 users, 🛒 orders, ⏱ duration, 🔢 numeric metric\n\n");

        sb.append("=== THEME ===\n");
        sb.append("Choose ONE theme that fits the data domain:\n");
        sb.append("  \"blue\"   — corporate/finance/sales data\n");
        sb.append("  \"green\"  — health/environment/growth data\n");
        sb.append("  \"purple\" — tech/analytics/product data\n");
        sb.append("  \"orange\" — retail/ecommerce/operations data\n\n");

        sb.append("=== RESPONSE FORMAT ===\n");
        sb.append("Single valid JSON object only. No markdown fences. No extra text before or after.\n\n");
        sb.append("{\n");
        sb.append("  \"theme\": \"blue\",\n");
        sb.append("  \"metrics\": [\n");
        sb.append("    { \"label\": \"Total Records\", \"value\": \"<actual rowCount>\", \"icon\": \"📊\" },\n");
        sb.append("    { \"label\": \"<meaningful metric>\", \"value\": \"<real computed value>\", \"icon\": \"<emoji>\" }\n");
        sb.append("  ],\n");
        sb.append("  \"charts\": [\n");
        sb.append("    {\n");
        sb.append("      \"type\": \"bar\",\n");
        sb.append("      \"title\": \"<descriptive chart title>\",\n");
        sb.append("      \"datasetIndex\": 0,\n");
        sb.append("      \"xField\": \"<exact column name — string/date>\",\n");
        sb.append("      \"yField\": \"<exact column name — isNumeric=true ONLY>\",\n");
        sb.append("      \"aggregation\": \"sum\"\n");
        sb.append("    },\n");
        sb.append("    {\n");
        sb.append("      \"type\": \"pie\",\n");
        sb.append("      \"title\": \"<descriptive chart title>\",\n");
        sb.append("      \"datasetIndex\": 0,\n");
        sb.append("      \"labelField\": \"<exact column name — string/category>\",\n");
        sb.append("      \"valueField\": \"<exact column name — isNumeric=true ONLY>\",\n");
        sb.append("      \"aggregation\": \"count\"\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");

        return sb.toString();
    }
}

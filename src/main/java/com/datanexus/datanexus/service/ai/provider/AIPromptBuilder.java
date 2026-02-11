package com.datanexus.datanexus.service.ai.provider;

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

    public static String buildPrompt(AIRequest request) {
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

package com.datanexus.datanexus.service.datasource.schema;

import com.datanexus.datanexus.service.datasource.DataSourceType;
import lombok.Builder;
import lombok.Getter;

/**
 * Unified schema representation for all data sources
 */
@Getter
@Builder
public class SourceSchema {
    private String sourceId;
    private String sourceName;
    private DataSourceType sourceType;
    private Object schemaData; // Database: DatabaseSchema, MCP: MCPCapabilities, etc.

    /**
     * Create schema from database
     */
    public static SourceSchema fromDatabase(String connectionId, String connectionName,
            Object databaseSchema) {
        return SourceSchema.builder()
                .sourceId(connectionId)
                .sourceName(connectionName)
                .sourceType(DataSourceType.DATABASE)
                .schemaData(databaseSchema)
                .build();
    }

    /**
     * Create schema from MCP server
     */
    public static SourceSchema fromMCP(String connectionId, String connectionName,
            MCPCapabilities capabilities) {
        return SourceSchema.builder()
                .sourceId(connectionId)
                .sourceName(connectionName)
                .sourceType(DataSourceType.MCP_SERVER)
                .schemaData(capabilities)
                .build();
    }
}

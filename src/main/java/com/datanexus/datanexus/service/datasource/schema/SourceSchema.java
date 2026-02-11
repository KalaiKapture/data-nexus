package com.datanexus.datanexus.service.datasource.schema;

import com.datanexus.datanexus.service.datasource.DataSourceType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

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

    // Sample data per table/collection: tableName → list of row maps
    private Map<String, List<Map<String, Object>>> sampleData;

    // For NoSQL databases
    private List<CollectionSchema> collections;

    // For key-value stores
    private List<KeyPatternSchema> keyPatterns;

    /**
     * Create schema from database (without sample data)
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
     * Create schema from database with sample data
     */
    public static SourceSchema fromDatabase(String connectionId, String connectionName,
            Object databaseSchema, Map<String, List<Map<String, Object>>> sampleData) {
        return SourceSchema.builder()
                .sourceId(connectionId)
                .sourceName(connectionName)
                .sourceType(DataSourceType.DATABASE)
                .schemaData(databaseSchema)
                .sampleData(sampleData)
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

    /**
     * Schema for a collection (MongoDB, Elasticsearch index, etc.)
     */
    @Getter
    @Builder
    public static class CollectionSchema {
        private String name;
        private String sampleDocument; // JSON string
        private List<String> indexes;
        private Long documentCount;
        private List<FieldSchema> fields;
    }

    /**
     * Schema for a field in a document
     */
    @Getter
    @Builder
    public static class FieldSchema {
        private String name;
        private String type;
        private Boolean indexed;
        private Boolean required;
    }

    /**
     * Schema for key patterns in Redis
     */
    @Getter
    @Builder
    public static class KeyPatternSchema {
        private String pattern;
        private String type; // "string", "hash", "list", "set", "zset"
        private Long count;
        private Integer database;
    }
}

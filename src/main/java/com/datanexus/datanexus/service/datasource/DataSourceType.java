package com.datanexus.datanexus.service.datasource;

/**
 * Represents the type of data source
 */
public enum DataSourceType {
    DATABASE("Database"),
    MCP_SERVER("MCP Server"),
    REST_API("REST API"),
    GRAPHQL_API("GraphQL API"),
    MONGODB("MongoDB"),
    REDIS("Redis"),
    ELASTICSEARCH("Elasticsearch"),
    BIGQUERY("BigQuery");

    private final String displayName;

    DataSourceType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

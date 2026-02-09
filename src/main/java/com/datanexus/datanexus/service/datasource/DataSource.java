package com.datanexus.datanexus.service.datasource;

import com.datanexus.datanexus.service.datasource.schema.SourceSchema;

/**
 * Abstraction for all data sources (databases, MCP servers, APIs, etc.)
 */
public interface DataSource {

    /**
     * Get unique identifier for this data source
     */
    String getId();

    /**
     * Get display name for this data source
     */
    String getName();

    /**
     * Get the type of this data source
     */
    DataSourceType getType();

    /**
     * Extract schema/capabilities from this data source
     * For databases: tables, columns, keys
     * For MCP servers: tools, resources
     * For APIs: endpoints, operations
     */
    SourceSchema extractSchema();

    /**
     * Execute a request against this data source
     */
    ExecutionResult execute(DataRequest request);

    /**
     * Check if this data source is currently available/healthy
     */
    boolean isAvailable();

    /**
     * Get connection details (for logging/debugging)
     */
    String getConnectionInfo();
}

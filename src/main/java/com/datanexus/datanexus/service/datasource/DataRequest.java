package com.datanexus.datanexus.service.datasource;

/**
 * Represents a request to a data source
 * Different implementations for SQL queries, MCP tool calls, API requests, etc.
 */
public interface DataRequest {
    /**
     * Get the type of this request
     */
    DataRequestType getRequestType();

    /**
     * Get human-readable description of this request
     */
    String getDescription();
}

package com.datanexus.datanexus.service.datasource;

/**
 * Represents a request to a data source.
 * Different implementations for SQL queries, MCP tool calls, API requests, etc.
 * <p>
 * Supports cross-database chaining via step ordering and variable substitution.
 */
public interface DataRequest {
    /** Get the type of this request */
    DataRequestType getRequestType();

    /** Get human-readable description of this request */
    String getDescription();

    /** Target connection/source ID (as specified by the AI) */
    default String getSourceId() {
        return null;
    }

    /** Execution step number (1-based). Steps execute in order. */
    default Integer getStep() {
        return null;
    }

    /** Step number this request depends on (null = independent) */
    default Integer getDependsOn() {
        return null;
    }

    /** Variable name to store this step's result (e.g. "$user_id") */
    default String getOutputAs() {
        return null;
    }

    /** Column to extract from result for the output variable */
    default String getOutputField() {
        return null;
    }
}

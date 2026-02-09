package com.datanexus.datanexus.service.datasource;

/**
 * Type of data request
 */
public enum DataRequestType {
    SQL_QUERY,
    MCP_TOOL_CALL,
    MCP_RESOURCE_READ,
    REST_API_CALL,
    GRAPHQL_QUERY
}

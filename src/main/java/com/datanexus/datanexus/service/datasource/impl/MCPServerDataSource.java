package com.datanexus.datanexus.service.datasource.impl;

import com.datanexus.datanexus.entity.DatabaseConnection;
import com.datanexus.datanexus.service.datasource.*;
import com.datanexus.datanexus.service.datasource.request.MCPResourceRead;
import com.datanexus.datanexus.service.datasource.request.MCPToolCall;
import com.datanexus.datanexus.service.datasource.schema.MCPCapabilities;
import com.datanexus.datanexus.service.datasource.schema.SourceSchema;
import com.datanexus.datanexus.service.mcp.MCPClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MCP Server implementation of DataSource
 */
@Slf4j
@RequiredArgsConstructor
public class MCPServerDataSource implements DataSource {

    private final DatabaseConnection connection;
    private final MCPClient mcpClient;
    private final ObjectMapper objectMapper;

    @Override
    public String getId() {
        return connection.getId().toString();
    }

    @Override
    public String getName() {
        return connection.getName();
    }

    @Override
    public DataSourceType getType() {
        return DataSourceType.MCP_SERVER;
    }

    @Override
    public SourceSchema extractSchema() {
        log.info("Extracting capabilities for MCP server: {}", connection.getName());
        MCPCapabilities capabilities = mcpClient.getCapabilities(connection);
        return SourceSchema.fromMCP(
                connection.getId().toString(),
                connection.getName(),
                capabilities);
    }

    @Override
    public ExecutionResult execute(DataRequest request) {
        if (request instanceof MCPToolCall) {
            return executeMCPToolCall((MCPToolCall) request);
        } else if (request instanceof MCPResourceRead) {
            return executeMCPResourceRead((MCPResourceRead) request);
        }

        throw new IllegalArgumentException(
                "MCPServerDataSource only supports MCPToolCall and MCPResourceRead requests");
    }

    private ExecutionResult executeMCPToolCall(MCPToolCall toolCall) {
        log.info("Invoking MCP tool '{}' on server: {}",
                toolCall.getToolName(), connection.getName());

        return mcpClient.invokeTool(connection, toolCall);
    }

    private ExecutionResult executeMCPResourceRead(MCPResourceRead resourceRead) {
        log.info("Reading MCP resource '{}' from server: {}",
                resourceRead.getUri(), connection.getName());

        return mcpClient.readResource(connection, resourceRead);
    }

    @Override
    public boolean isAvailable() {
        try {
            // Check if MCP server is reachable by getting capabilities
            mcpClient.getCapabilities(connection);
            return true;
        } catch (Exception e) {
            log.warn("MCP server {} is not available: {}", connection.getName(), e.getMessage());
            return false;
        }
    }

    @Override
    public String getConnectionInfo() {
        return String.format("MCP Server: %s (%s)",
                connection.getName(),
                connection.getHost());
    }

    /**
     * Factory for creating MCPServerDataSource instances
     */
    @Component
    @RequiredArgsConstructor
    public static class Factory {
        private final MCPClient mcpClient;
        private final ObjectMapper objectMapper;

        public MCPServerDataSource create(DatabaseConnection connection) {
            return new MCPServerDataSource(connection, mcpClient, objectMapper);
        }
    }
}

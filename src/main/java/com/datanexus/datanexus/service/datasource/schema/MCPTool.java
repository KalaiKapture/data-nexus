package com.datanexus.datanexus.service.datasource.schema;

import lombok.Builder;
import lombok.Getter;

/**
 * Represents an MCP tool (function/action that can be invoked)
 */
@Getter
@Builder
public class MCPTool {
    private String name;
    private String description;
    private Object inputSchema; // JSON Schema for tool parameters
}

package com.datanexus.datanexus.service.datasource.request;

import com.datanexus.datanexus.service.datasource.DataRequest;
import com.datanexus.datanexus.service.datasource.DataRequestType;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * MCP tool invocation request.
 * Supports cross-database chaining via step/dependsOn/outputAs fields.
 */
@Getter
@Builder
public class MCPToolCall implements DataRequest {
    private String toolName;
    private Map<String, Object> arguments;
    private String explanation;

    // Chaining fields
    private String sourceId;
    private Integer step;
    private Integer dependsOn;
    private String outputAs;
    private String outputField;

    @Override
    public DataRequestType getRequestType() {
        return DataRequestType.MCP_TOOL_CALL;
    }

    @Override
    public String getDescription() {
        return explanation != null ? explanation : "MCP Tool: " + toolName;
    }
}

package com.datanexus.datanexus.service.datasource.request;

import com.datanexus.datanexus.service.datasource.DataRequest;
import com.datanexus.datanexus.service.datasource.DataRequestType;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * MCP tool invocation request
 */
@Getter
@Builder
public class MCPToolCall implements DataRequest {
    private String toolName;
    private Map<String, Object> arguments;
    private String explanation;

    @Override
    public DataRequestType getRequestType() {
        return DataRequestType.MCP_TOOL_CALL;
    }

    @Override
    public String getDescription() {
        return explanation != null ? explanation : "MCP Tool: " + toolName;
    }
}

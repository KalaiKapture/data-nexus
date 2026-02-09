package com.datanexus.datanexus.service.datasource.schema;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * MCP server capabilities (tools and resources)
 */
@Getter
@Builder
public class MCPCapabilities {
    private String connectionId;
    private String serverName;
    private List<MCPTool> tools;
    private List<MCPResource> resources;
}

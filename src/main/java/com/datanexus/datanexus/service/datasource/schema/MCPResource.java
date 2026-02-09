package com.datanexus.datanexus.service.datasource.schema;

import lombok.Builder;
import lombok.Getter;

/**
 * Represents an MCP resource (data that can be read)
 */
@Getter
@Builder
public class MCPResource {
    private String uri;
    private String name;
    private String description;
    private String mimeType;
}

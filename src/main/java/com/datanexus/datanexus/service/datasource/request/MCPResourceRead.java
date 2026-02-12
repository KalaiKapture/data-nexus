package com.datanexus.datanexus.service.datasource.request;

import com.datanexus.datanexus.service.datasource.DataRequest;
import com.datanexus.datanexus.service.datasource.DataRequestType;
import lombok.Builder;
import lombok.Getter;

/**
 * MCP resource read request.
 * Supports cross-database chaining via step/dependsOn/outputAs fields.
 */
@Getter
@Builder
public class MCPResourceRead implements DataRequest {
    private String uri;
    private String explanation;

    // Chaining fields
    private String sourceId;
    private Integer step;
    private Integer dependsOn;
    private String outputAs;
    private String outputField;

    @Override
    public DataRequestType getRequestType() {
        return DataRequestType.MCP_RESOURCE_READ;
    }

    @Override
    public String getDescription() {
        return explanation != null ? explanation : "MCP Resource: " + uri;
    }
}

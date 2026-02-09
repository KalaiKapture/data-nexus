package com.datanexus.datanexus.service.datasource.request;

import com.datanexus.datanexus.service.datasource.DataRequest;
import com.datanexus.datanexus.service.datasource.DataRequestType;
import lombok.Builder;
import lombok.Getter;

/**
 * MCP resource read request
 */
@Getter
@Builder
public class MCPResourceRead implements DataRequest {
    private String uri;
    private String explanation;

    @Override
    public DataRequestType getRequestType() {
        return DataRequestType.MCP_RESOURCE_READ;
    }

    @Override
    public String getDescription() {
        return explanation != null ? explanation : "MCP Resource: " + uri;
    }
}

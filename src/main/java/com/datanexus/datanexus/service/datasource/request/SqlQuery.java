package com.datanexus.datanexus.service.datasource.request;

import com.datanexus.datanexus.service.datasource.DataRequest;
import com.datanexus.datanexus.service.datasource.DataRequestType;
import lombok.Builder;
import lombok.Getter;

/**
 * SQL query request for database data sources.
 * Supports cross-database chaining via step/dependsOn/outputAs fields.
 */
@Getter
@Builder
public class SqlQuery implements DataRequest {
    private String sql;
    private String explanation;

    // Chaining fields
    private String sourceId;
    private Integer step;
    private Integer dependsOn;
    private String outputAs;
    private String outputField;

    @Override
    public DataRequestType getRequestType() {
        return DataRequestType.SQL_QUERY;
    }

    @Override
    public String getDescription() {
        return explanation != null ? explanation : "SQL Query: " + sql;
    }
}

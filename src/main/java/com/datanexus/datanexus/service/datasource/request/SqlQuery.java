package com.datanexus.datanexus.service.datasource.request;

import com.datanexus.datanexus.service.datasource.DataRequest;
import com.datanexus.datanexus.service.datasource.DataRequestType;
import lombok.Builder;
import lombok.Getter;

/**
 * SQL query request for database data sources
 */
@Getter
@Builder
public class SqlQuery implements DataRequest {
    private String sql;
    private String explanation;

    @Override
    public DataRequestType getRequestType() {
        return DataRequestType.SQL_QUERY;
    }

    @Override
    public String getDescription() {
        return explanation != null ? explanation : "SQL Query: " + sql;
    }
}

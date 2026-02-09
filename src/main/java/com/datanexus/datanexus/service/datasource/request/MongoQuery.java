package com.datanexus.datanexus.service.datasource.request;

import com.datanexus.datanexus.service.datasource.DataRequest;
import com.datanexus.datanexus.service.datasource.DataRequestType;
import lombok.Builder;
import lombok.Getter;

/**
 * Request for MongoDB queries
 */
@Getter
@Builder
public class MongoQuery implements DataRequest {
    private String collection;
    private String operation; // "find", "aggregate", "count", "update", "delete"
    private String filter; // JSON string for filter/query
    private Integer limit;
    private Integer skip;
    private String sort; // JSON string for sort
    private String projection; // JSON string for projection
    private String explanation; // Human-readable explanation of the query

    @Override
    public DataRequestType getRequestType() {
        return DataRequestType.MONGO_QUERY;
    }

    @Override
    public String getDescription() {
        return explanation != null ? explanation
                : String.format("MongoDB %s on collection '%s'", operation, collection);
    }
}

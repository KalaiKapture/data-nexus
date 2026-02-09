package com.datanexus.datanexus.service.datasource.request;

import com.datanexus.datanexus.service.datasource.DataRequest;
import com.datanexus.datanexus.service.datasource.DataRequestType;
import lombok.Builder;
import lombok.Getter;

/**
 * Request for Elasticsearch queries
 */
@Getter
@Builder
public class ElasticsearchQuery implements DataRequest {
    private String index; // Target index
    private String query; // JSON Query DSL
    private Integer size; // Result size (default 100)
    private Integer from; // Offset
    private String sort; // JSON sort specification
    private String explanation; // Human-readable explanation

    @Override
    public DataRequestType getRequestType() {
        return DataRequestType.ELASTICSEARCH_QUERY;
    }

    @Override
    public String getDescription() {
        return explanation != null ? explanation : String.format("Elasticsearch query on index '%s'", index);
    }
}

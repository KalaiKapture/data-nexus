package com.datanexus.datanexus.service.datasource.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.datanexus.datanexus.entity.DatabaseConnection;
import com.datanexus.datanexus.service.datasource.*;
import com.datanexus.datanexus.service.datasource.request.ElasticsearchQuery;
import com.datanexus.datanexus.service.datasource.schema.SourceSchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.util.*;

/**
 * Elasticsearch implementation of DataSource
 */
@Slf4j
@RequiredArgsConstructor
public class ElasticsearchDataSource implements DataSource {

    private final DatabaseConnection connection;
    private final ObjectMapper objectMapper;
    private ElasticsearchClient esClient;

    @Override
    public String getId() {
        return connection.getId().toString();
    }

    @Override
    public String getName() {
        return connection.getName();
    }

    @Override
    public DataSourceType getType() {
        return DataSourceType.ELASTICSEARCH;
    }

    @Override
    public boolean isAvailable() {
        try {
            getElasticsearchClient().ping().value();
            return true;
        } catch (Exception e) {
            log.warn("Elasticsearch connection not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getConnectionInfo() {
        return String.format("Elasticsearch %s:%s",
                connection.getHost(),
                connection.getPort());
    }

    @Override
    public SourceSchema extractSchema() {
        log.info("Extracting schema for Elasticsearch: {}", connection.getName());

        try {
            ElasticsearchClient client = getElasticsearchClient();
            List<SourceSchema.CollectionSchema> indices = new ArrayList<>();

            // Get all indices
            var indicesResponse = client.cat().indices();

            for (var indexInfo : indicesResponse.valueBody()) {
                String indexName = indexInfo.index();

                if (indexName == null || indexName.startsWith(".")) {
                    // Skip system indices
                    continue;
                }

                try {
                    // Get mapping for this index
                    GetMappingResponse mappingResponse = client.indices()
                            .getMapping(m -> m.index(indexName));

                    var indexMappings = mappingResponse.get(indexName);
                    if (indexMappings == null)
                        continue;

                    var mappings = indexMappings.mappings();
                    if (mappings == null || mappings.properties() == null)
                        continue;

                    // Extract fields from mappings
                    List<SourceSchema.FieldSchema> fields = new ArrayList<>();
                    for (Map.Entry<String, Property> entry : mappings.properties().entrySet()) {
                        String fieldName = entry.getKey();
                        Property property = entry.getValue();

                        String fieldType = getPropertyType(property);

                        fields.add(SourceSchema.FieldSchema.builder()
                                .name(fieldName)
                                .type(fieldType)
                                .build());
                    }

                    // Get document count
                    var countResponse = client.count(c -> c.index(indexName));
                    long docCount = countResponse.count();

                    indices.add(SourceSchema.CollectionSchema.builder()
                            .name(indexName)
                            .documentCount(docCount)
                            .fields(fields)
                            .sampleDocument("{}") // Elasticsearch doesn't provide sample docs in mapping
                            .build());

                } catch (Exception e) {
                    log.warn("Failed to extract schema for index {}: {}", indexName, e.getMessage());
                }
            }

            return SourceSchema.builder()
                    .sourceId(connection.getId().toString())
                    .sourceName(connection.getName())
                    .sourceType(DataSourceType.ELASTICSEARCH)
                    .collections(indices)
                    .build();

        } catch (Exception e) {
            log.error("Failed to extract Elasticsearch schema: {}", e.getMessage(), e);
            throw new RuntimeException("Schema extraction failed: " + e.getMessage(), e);
        }
    }

    private String getPropertyType(Property property) {
        // Extract the type from the Property variant
        if (property.isText())
            return "text";
        if (property.isKeyword())
            return "keyword";
        if (property.isLong())
            return "long";
        if (property.isInteger())
            return "integer";
        if (property.isShort())
            return "short";
        if (property.isByte())
            return "byte";
        if (property.isDouble())
            return "double";
        if (property.isFloat())
            return "float";
        if (property.isBoolean())
            return "boolean";
        if (property.isDate())
            return "date";
        if (property.isObject())
            return "object";
        if (property.isNested())
            return "nested";
        if (property.isGeoPoint())
            return "geo_point";
        return "unknown";
    }

    @Override
    public ExecutionResult execute(DataRequest request) {
        if (!(request instanceof ElasticsearchQuery)) {
            return ExecutionResult.builder()
                    .success(false)
                    .errorMessage("Invalid request type for Elasticsearch. Expected ElasticsearchQuery.")
                    .build();
        }

        ElasticsearchQuery esQuery = (ElasticsearchQuery) request;
        long startTime = System.currentTimeMillis();

        try {
            ElasticsearchClient client = getElasticsearchClient();
            String index = esQuery.getIndex();
            String queryDsl = esQuery.getQuery(); // JSON Query DSL

            List<Map<String, Object>> results = new ArrayList<>();

            if (queryDsl != null && !queryDsl.isEmpty()) {
                // Execute query using Query DSL
                SearchResponse<Map> response = client.search(s -> s
                        .index(index)
                        .withJson(new StringReader(queryDsl))
                        .size(esQuery.getSize() != null ? esQuery.getSize() : 100),
                        Map.class);

                if (response.hits() != null && response.hits().hits() != null) {
                    response.hits().hits().forEach(hit -> {
                        Map<String, Object> doc = new HashMap<>();
                        doc.put("_id", hit.id());
                        doc.put("_index", hit.index());
                        doc.put("_score", hit.score());
                        if (hit.source() != null) {
                            doc.putAll(hit.source());
                        }
                        results.add(doc);
                    });
                }
            } else {
                // Match all query
                SearchResponse<Map> response = client.search(s -> s
                        .index(index)
                        .size(esQuery.getSize() != null ? esQuery.getSize() : 100),
                        Map.class);

                if (response.hits() != null && response.hits().hits() != null) {
                    response.hits().hits().forEach(hit -> {
                        Map<String, Object> doc = new HashMap<>();
                        doc.put("_id", hit.id());
                        doc.put("_index", hit.index());
                        doc.put("_score", hit.score());
                        if (hit.source() != null) {
                            doc.putAll(hit.source());
                        }
                        results.add(doc);
                    });
                }
            }

            long executionTime = System.currentTimeMillis() - startTime;

            return ExecutionResult.builder()
                    .success(true)
                    .data(results)
                    .rowCount(results.size())
                    .executionTimeMs(executionTime)
                    .build();

        } catch (Exception e) {
            log.error("Elasticsearch query execution failed: {}", e.getMessage(), e);
            long executionTime = System.currentTimeMillis() - startTime;
            return ExecutionResult.builder()
                    .success(false)
                    .errorMessage("Query execution failed: " + e.getMessage())
                    .executionTimeMs(executionTime)
                    .build();
        }
    }

    private synchronized ElasticsearchClient getElasticsearchClient() {
        if (esClient == null) {
            log.info("Connecting to Elasticsearch: {}", connection.getHost());

            String scheme = "http"; // Default
            String host = connection.getHost();
            int port = Integer.parseInt(connection.getPort() != null ? connection.getPort() : "9200");

            // Check for HTTPS in otherDetails
            if (connection.getOtherDetails() != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> details = objectMapper.readValue(
                            connection.getOtherDetails(), Map.class);
                    if (details.containsKey("scheme")) {
                        scheme = details.get("scheme").toString();
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse otherDetails: {}", e.getMessage());
                }
            }

            // Create REST client with auth
            RestClient restClient;
            if (connection.getUsername() != null && !connection.getUsername().isEmpty()) {
                BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(
                        AuthScope.ANY,
                        new UsernamePasswordCredentials(
                                connection.getUsername(),
                                connection.getPassword()));

                restClient = RestClient.builder(new HttpHost(host, port, scheme))
                        .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                                .setDefaultCredentialsProvider(credentialsProvider))
                        .build();
            } else {
                restClient = RestClient.builder(new HttpHost(host, port, scheme)).build();
            }

            RestClientTransport transport = new RestClientTransport(
                    restClient,
                    new JacksonJsonpMapper());

            esClient = new ElasticsearchClient(transport);
        }
        return esClient;
    }

    public void close() {
        if (esClient != null) {
            try {
                esClient._transport().close();
                esClient = null;
            } catch (Exception e) {
                log.error("Failed to close Elasticsearch client: {}", e.getMessage());
            }
        }
    }

    /**
     * Factory for creating ElasticsearchDataSource instances
     */
    @Component
    @RequiredArgsConstructor
    public static class Factory {
        private final ObjectMapper objectMapper;

        public ElasticsearchDataSource create(DatabaseConnection connection) {
            return new ElasticsearchDataSource(connection, objectMapper);
        }
    }
}

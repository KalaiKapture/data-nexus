package com.datanexus.datanexus.service.datasource.impl;

import com.datanexus.datanexus.entity.DatabaseConnection;
import com.datanexus.datanexus.service.datasource.*;
import com.datanexus.datanexus.service.datasource.request.MongoQuery;
import com.datanexus.datanexus.service.datasource.schema.SourceSchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * MongoDB implementation of DataSource
 */
@Slf4j
@RequiredArgsConstructor
public class MongoDBDataSource implements DataSource {

    private final DatabaseConnection connection;
    private final ObjectMapper objectMapper;
    private MongoClient mongoClient;

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
        return DataSourceType.MONGODB;
    }

    @Override
    public boolean isAvailable() {
        try {
            getMongoClient().listDatabaseNames().first();
            return true;
        } catch (Exception e) {
            log.warn("MongoDB connection not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getConnectionInfo() {
        return String.format("MongoDB %s:%s/%s",
                connection.getHost(),
                connection.getPort(),
                connection.getDatabase());
    }

    @Override
    public SourceSchema extractSchema() {
        log.info("Extracting schema for MongoDB: {}", connection.getName());

        try {
            MongoClient client = getMongoClient();
            MongoDatabase database = client.getDatabase(connection.getDatabase());

            List<SourceSchema.CollectionSchema> collections = new ArrayList<>();

            // Iterate through all collections
            for (String collectionName : database.listCollectionNames()) {
                try {
                    // Sample one document to understand structure
                    Document sampleDoc = database.getCollection(collectionName)
                            .find()
                            .limit(1)
                            .first();

                    // Get indexes
                    List<String> indexes = new ArrayList<>();
                    database.getCollection(collectionName)
                            .listIndexes()
                            .forEach(index -> {
                                Document keys = index.get("key", Document.class);
                                if (keys != null) {
                                    indexes.addAll(keys.keySet());
                                }
                            });

                    // Estimate document count
                    long documentCount = database.getCollection(collectionName).estimatedDocumentCount();

                    collections.add(SourceSchema.CollectionSchema.builder()
                            .name(collectionName)
                            .sampleDocument(sampleDoc != null ? sampleDoc.toJson() : "{}")
                            .indexes(indexes)
                            .documentCount(documentCount)
                            .fields(sampleDoc != null ? extractFields(sampleDoc) : new ArrayList<>())
                            .build());

                } catch (Exception e) {
                    log.warn("Failed to extract schema for collection {}: {}", collectionName, e.getMessage());
                }
            }

            return SourceSchema.builder()
                    .sourceId(connection.getId().toString())
                    .sourceName(connection.getName())
                    .sourceType(DataSourceType.MONGODB)
                    .collections(collections)
                    .build();

        } catch (Exception e) {
            log.error("Failed to extract MongoDB schema: {}", e.getMessage(), e);
            throw new RuntimeException("Schema extraction failed: " + e.getMessage(), e);
        }
    }

    private List<SourceSchema.FieldSchema> extractFields(Document document) {
        List<SourceSchema.FieldSchema> fields = new ArrayList<>();
        if (document == null)
            return fields;

        for (Map.Entry<String, Object> entry : document.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();
            String fieldType = value != null ? value.getClass().getSimpleName() : "null";

            fields.add(SourceSchema.FieldSchema.builder()
                    .name(fieldName)
                    .type(fieldType)
                    .build());
        }

        return fields;
    }

    @Override
    public ExecutionResult execute(DataRequest request) {
        if (!(request instanceof MongoQuery)) {
            return ExecutionResult.builder()
                    .success(false)
                    .errorMessage("Invalid request type for MongoDB. Expected MongoQuery.")
                    .build();
        }

        MongoQuery mongoQuery = (MongoQuery) request;
        long startTime = System.currentTimeMillis();

        try {
            MongoClient client = getMongoClient();
            MongoDatabase database = client.getDatabase(connection.getDatabase());

            // Parse operation type from query
            String operation = mongoQuery.getOperation(); // "find", "aggregate", "count", etc.
            String collection = mongoQuery.getCollection();
            String filter = mongoQuery.getFilter(); // JSON filter

            List<Map<String, Object>> results = new ArrayList<>();

            switch (operation.toLowerCase()) {
                case "find" -> {
                    Document filterDoc = Document.parse(filter != null ? filter : "{}");
                    database.getCollection(collection)
                            .find(filterDoc)
                            .limit(mongoQuery.getLimit() != null ? mongoQuery.getLimit() : 100)
                            .forEach(doc -> results.add(documentToMap(doc)));
                }
                case "count" -> {
                    Document filterDoc = Document.parse(filter != null ? filter : "{}");
                    long count = database.getCollection(collection).countDocuments(filterDoc);
                    results.add(Map.of("count", count));
                }
                case "aggregate" -> {
                    // For aggregate, filter should be a JSON array of pipeline stages
                    List<Document> pipeline = parsePipeline(filter);
                    database.getCollection(collection)
                            .aggregate(pipeline)
                            .forEach(doc -> results.add(documentToMap(doc)));
                }
                default -> throw new IllegalArgumentException("Unsupported operation: " + operation);
            }

            long executionTime = System.currentTimeMillis() - startTime;

            return ExecutionResult.builder()
                    .success(true)
                    .data(results)
                    .rowCount(results.size())
                    .executionTimeMs(executionTime)
                    .build();

        } catch (Exception e) {
            log.error("MongoDB query execution failed: {}", e.getMessage(), e);
            long executionTime = System.currentTimeMillis() - startTime;
            return ExecutionResult.builder()
                    .success(false)
                    .errorMessage("Query execution failed: " + e.getMessage())
                    .executionTimeMs(executionTime)
                    .build();
        }
    }

    private Map<String, Object> documentToMap(Document document) {
        return new HashMap<>(document);
    }

    private List<Document> parsePipeline(String pipelineJson) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pipelineList = objectMapper.readValue(pipelineJson, List.class);
            List<Document> pipeline = new ArrayList<>();
            for (Map<String, Object> stage : pipelineList) {
                pipeline.add(new Document(stage));
            }
            return pipeline;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse aggregation pipeline: " + e.getMessage(), e);
        }
    }

    private synchronized MongoClient getMongoClient() {
        if (mongoClient == null) {
            String connectionString = buildConnectionString();
            log.info("Connecting to MongoDB: {}", connection.getHost());
            mongoClient = MongoClients.create(connectionString);
        }
        return mongoClient;
    }

    private String buildConnectionString() {
        // Build MongoDB connection string
        StringBuilder sb = new StringBuilder("mongodb://");

        if (connection.getUsername() != null && !connection.getUsername().isEmpty()) {
            sb.append(connection.getUsername());
            if (connection.getPassword() != null) {
                sb.append(":").append(connection.getPassword());
            }
            sb.append("@");
        }

        sb.append(connection.getHost());
        if (connection.getPort() != null && !connection.getPort().isEmpty()) {
            sb.append(":").append(connection.getPort());
        }

        sb.append("/").append(connection.getDatabase());

        // Add auth source if specified in otherDetails
        if (connection.getOtherDetails() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> details = objectMapper.readValue(
                        connection.getOtherDetails(), Map.class);

                if (details.containsKey("authSource")) {
                    sb.append("?authSource=").append(details.get("authSource"));
                }
            } catch (Exception e) {
                log.warn("Failed to parse otherDetails: {}", e.getMessage());
            }
        }

        return sb.toString();
    }

    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            mongoClient = null;
        }
    }

    /**
     * Factory for creating MongoDBDataSource instances
     */
    @Component
    @RequiredArgsConstructor
    public static class Factory {
        private final ObjectMapper objectMapper;

        public MongoDBDataSource create(DatabaseConnection connection) {
            return new MongoDBDataSource(connection, objectMapper);
        }
    }
}

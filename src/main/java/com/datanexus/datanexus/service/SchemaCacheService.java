package com.datanexus.datanexus.service;

import com.datanexus.datanexus.entity.DatabaseConnection;
import com.datanexus.datanexus.entity.SchemaCache;
import com.datanexus.datanexus.repository.DatabaseConnectionRepository;
import com.datanexus.datanexus.repository.SchemaCacheRepository;
import com.datanexus.datanexus.service.ai.QueryExecutionService;
import com.datanexus.datanexus.service.ai.SchemaService;
import com.datanexus.datanexus.service.datasource.schema.SourceSchema;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchemaCacheService {

    private final SchemaCacheRepository schemaCacheRepository;
    private final DatabaseConnectionRepository connectionRepository;
    private final SchemaService schemaService;
    private final QueryExecutionService queryExecutionService;
    private final ObjectMapper objectMapper;

    private static final int SAMPLE_ROW_LIMIT = 5;

    /**
     * Cache schema and sample data for a connection.
     * Connects to the external DB, extracts schema + sample rows, and persists to
     * schema_cache.
     */
    public void cacheSchema(Long connectionId, Long userId) {
        log.info("Caching schema and sample data for connection {} (user {})", connectionId, userId);

        DatabaseConnection conn = connectionRepository.findByIdAndUserId(connectionId, userId);
        if (conn == null) {
            log.warn("Connection {} not found for user {}", connectionId, userId);
            return;
        }

        try {
            // Extract schema
            SchemaService.DatabaseSchema dbSchema = schemaService.extractSchema(conn);

            // Extract sample data (5 rows per table)
            Map<String, List<Map<String, Object>>> sampleData = extractSampleData(conn, dbSchema);

            // Serialize
            String schemaJson = objectMapper.writeValueAsString(dbSchema);
            String sampleDataJson = objectMapper.writeValueAsString(sampleData);

            // Upsert into schema_cache
            Optional<SchemaCache> existing = schemaCacheRepository.findByConnectionIdAndUserId(connectionId, userId);
            SchemaCache cache;
            if (existing.isPresent()) {
                cache = existing.get();
                cache.setSchemaJson(schemaJson);
                cache.setSampleDataJson(sampleDataJson);
                cache.setDataSourceType(conn.getType());
                cache.setCachedAt(Instant.now());
            } else {
                cache = SchemaCache.builder()
                        .connectionId(connectionId)
                        .userId(userId)
                        .dataSourceType(conn.getType())
                        .schemaJson(schemaJson)
                        .sampleDataJson(sampleDataJson)
                        .cachedAt(Instant.now())
                        .build();
            }

            schemaCacheRepository.save(cache);
            log.info("Successfully cached schema for connection {} with {} tables", connectionId,
                    dbSchema.getTables().size());

        } catch (Exception e) {
            log.error("Failed to cache schema for connection {}: {}", connectionId, e.getMessage(), e);
        }
    }

    /**
     * Retrieve cached schema + sample data as a SourceSchema.
     * Returns Optional.empty() if no cache exists for this connection+user.
     */
    public Optional<SourceSchema> getCachedSchema(Long connectionId, Long userId) {
        Optional<SchemaCache> cacheOpt = schemaCacheRepository.findByConnectionIdAndUserId(connectionId, userId);

        if (cacheOpt.isEmpty()) {
            return Optional.empty();
        }

        SchemaCache cache = cacheOpt.get();

        try {
            SchemaService.DatabaseSchema dbSchema = objectMapper.readValue(
                    cache.getSchemaJson(), SchemaService.DatabaseSchema.class);

            Map<String, List<Map<String, Object>>> sampleData = null;
            if (cache.getSampleDataJson() != null && !cache.getSampleDataJson().isBlank()) {
                sampleData = objectMapper.readValue(cache.getSampleDataJson(),
                        new TypeReference<Map<String, List<Map<String, Object>>>>() {
                        });
            }

            SourceSchema sourceSchema = SourceSchema.fromDatabase(
                    connectionId.toString(),
                    dbSchema.getConnectionName(),
                    dbSchema,
                    sampleData);

            return Optional.of(sourceSchema);

        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize cached schema for connection {}: {}", connectionId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Refresh schema cache: delete old cache, re-extract and re-cache.
     */
    public void refreshSchema(Long connectionId, Long userId) {
        log.info("Refreshing schema cache for connection {} (user {})", connectionId, userId);
        schemaCacheRepository.deleteByConnectionIdAndUserId(connectionId, userId);
        cacheSchema(connectionId, userId);
    }

    /**
     * Extract sample data (up to SAMPLE_ROW_LIMIT rows) from each table in the
     * schema.
     */
    private Map<String, List<Map<String, Object>>> extractSampleData(
            DatabaseConnection conn, SchemaService.DatabaseSchema dbSchema) {

        Map<String, List<Map<String, Object>>> sampleData = new LinkedHashMap<>();

        for (SchemaService.TableSchema table : dbSchema.getTables()) {
            try {
                String sql = "SELECT * FROM " + table.getTableName() + " LIMIT " + SAMPLE_ROW_LIMIT;
                QueryExecutionService.ExecutionResult result = queryExecutionService.execute(conn, sql);

                if (result.isSuccess() && result.getData() != null) {
                    sampleData.put(table.getTableName(), result.getData());
                } else {
                    sampleData.put(table.getTableName(), Collections.emptyList());
                    log.debug("No sample data retrieved for table {}: {}",
                            table.getTableName(), result.getErrorMessage());
                }
            } catch (Exception e) {
                log.warn("Failed to extract sample data for table {}: {}",
                        table.getTableName(), e.getMessage());
                sampleData.put(table.getTableName(), Collections.emptyList());
            }
        }

        return sampleData;
    }
}

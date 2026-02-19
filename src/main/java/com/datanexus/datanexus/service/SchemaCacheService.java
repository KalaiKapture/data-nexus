package com.datanexus.datanexus.service;

import com.datanexus.datanexus.entity.DatabaseConnection;
import com.datanexus.datanexus.repository.DatabaseConnectionRepository;
import com.datanexus.datanexus.service.ai.SchemaService;
import com.datanexus.datanexus.service.datasource.schema.SourceSchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Service that trains the LLM model with schema metadata by calling
 * POST /api/train/schema for every table in the connection's database.
 *
 * <p>No local DB cache is maintained. Each call to {@code cacheSchema()}
 * (or {@code refreshSchema()}) pushes the schema directly to the model
 * service so it always has up-to-date context.</p>
 *
 * <p>Train API payload per table:
 * <pre>
 * {
 *   "connectionId": "6",
 *   "tableName":    "order_details",
 *   "description":  "AI-generated table description",
 *   "columns": [
 *     { "name": "order_no", "type": "varchar", "description": "column description" }
 *   ]
 * }
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SchemaCacheService {

    private static final String TRAIN_API_BASE = "http://localhost:8082";
    private static final String TRAIN_API_PATH = "/api/train/schema";

    private final DatabaseConnectionRepository connectionRepository;
    private final SchemaService schemaService;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Extract schema from the external DB and train the model service
     * by calling POST /api/train/schema once per table.
     */
    public void cacheSchema(Long connectionId, Long userId) {
        log.info("Training schema for connection {} (user {})", connectionId, userId);

        DatabaseConnection conn = connectionRepository.findByIdAndUserId(connectionId, userId);
        if (conn == null) {
            log.warn("Connection {} not found for user {}", connectionId, userId);
            return;
        }

        try {
            SchemaService.DatabaseSchema dbSchema = schemaService.extractSchema(conn);
            trainSchema(connectionId, dbSchema);
            log.info("Successfully trained schema for connection {} ({} tables)",
                    connectionId, dbSchema.getTables().size());
        } catch (Exception e) {
            log.error("Failed to train schema for connection {}: {}", connectionId, e.getMessage(), e);
        }
    }

    /**
     * Re-extract schema and re-train the model service.
     * Equivalent to a cache refresh.
     */
    public void refreshSchema(Long connectionId, Long userId) {
        log.info("Refreshing schema training for connection {} (user {})", connectionId, userId);
        cacheSchema(connectionId, userId);
    }

    /**
     * Returns an empty Optional — schema is now stored in the model service,
     * not locally. Callers that rely on this will fall through to live extraction.
     *
     * @deprecated Schema is served from the LLM model service via the train API,
     *             not from a local cache. Use the model's context instead.
     */
    @Deprecated
    public Optional<SourceSchema> getCachedSchema(Long connectionId, Long userId) {
        // No local cache — return empty so caller falls back to live extraction
        return Optional.empty();
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    /**
     * For every table in the schema, build the train-API payload and POST it
     * to the model service.  Columns use the column name as their description
     * since the raw JDBC metadata has no semantic descriptions; a richer
     * description can be supplied by the model service itself.
     */
    private void trainSchema(Long connectionId, SchemaService.DatabaseSchema dbSchema) {
        for (SchemaService.TableSchema table : dbSchema.getTables()) {
            try {
                Map<String, Object> payload = buildTrainPayload(connectionId, table);
                String json = objectMapper.writeValueAsString(payload);
                postToTrainApi(json, table.getTableName(), connectionId);
            } catch (Exception e) {
                log.warn("Failed to train table {} for connection {}: {}",
                        table.getTableName(), connectionId, e.getMessage());
            }
        }
    }

    /**
     * Build the JSON payload for a single table.
     *
     * <p>Column description is derived from the column's data type and PK flag
     * since JDBC metadata carries no semantic descriptions.  If the model
     * service later enriches these, that enrichment lives in the model itself.
     */
    private Map<String, Object> buildTrainPayload(Long connectionId,
                                                   SchemaService.TableSchema table) {
        // Build columns array
        List<Map<String, Object>> columns = new ArrayList<>();
        for (SchemaService.ColumnSchema col : table.getColumns()) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("name", col.getName());
            c.put("type", col.getDataType() != null ? col.getDataType().toLowerCase() : "varchar");
            // description: human-readable hint built from available metadata
            c.put("description", buildColumnDescription(col));
            columns.add(c);
        }

        // Build table payload
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("connectionId", String.valueOf(connectionId));
        payload.put("tableName", table.getTableName());
        payload.put("description", buildTableDescription(table));
        payload.put("columns", columns);
        return payload;
    }

    /**
     * A short, consistent description for a table built from its primary keys
     * and column count — no AI call needed here.
     */
    private String buildTableDescription(SchemaService.TableSchema table) {
        StringBuilder sb = new StringBuilder();
        sb.append("Table ").append(table.getTableName());
        if (table.getPrimaryKeys() != null && !table.getPrimaryKeys().isEmpty()) {
            sb.append(" (PK: ").append(String.join(", ", table.getPrimaryKeys())).append(")");
        }
        sb.append(" with ").append(table.getColumns().size()).append(" columns");
        return sb.toString();
    }

    /**
     * A short, consistent description for a column built from its JDBC metadata.
     */
    private String buildColumnDescription(SchemaService.ColumnSchema col) {
        StringBuilder sb = new StringBuilder();
        if (col.isPrimaryKey()) sb.append("Primary key. ");
        sb.append(col.getName().replace("_", " "));
        if (!col.isNullable()) sb.append(" (required)");
        return sb.toString().trim();
    }

    /**
     * POST the JSON payload to the train API.
     * Logs a warning on non-2xx responses but does not throw — training
     * failures are non-fatal (the app can still function with live schema).
     */
    private void postToTrainApi(String jsonPayload, String tableName, Long connectionId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TRAIN_API_BASE + TRAIN_API_PATH))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.debug("Trained table '{}' for connection {} → HTTP {}",
                        tableName, connectionId, response.statusCode());
            } else {
                log.warn("Train API returned {} for table '{}' (connection {}): {}",
                        response.statusCode(), tableName, connectionId, response.body());
            }
        } catch (Exception e) {
            log.warn("HTTP error training table '{}' for connection {}: {}",
                    tableName, connectionId, e.getMessage());
        }
    }
}

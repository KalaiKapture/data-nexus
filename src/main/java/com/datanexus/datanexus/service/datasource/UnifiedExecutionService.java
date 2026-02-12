package com.datanexus.datanexus.service.datasource;

import com.datanexus.datanexus.dto.websocket.AnalyzeResponse;
import com.datanexus.datanexus.entity.DatabaseConnection;
import com.datanexus.datanexus.entity.User;
import com.datanexus.datanexus.repository.DatabaseConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Unified execution service for all types of data requests.
 * Supports cross-database query chaining via step ordering and $variable
 * substitution.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnifiedExecutionService {

    private final DataSourceRegistry dataSourceRegistry;
    private final DatabaseConnectionRepository connectionRepository;

    /**
     * Execute all data requests and return formatted results.
     * If requests have step/dependsOn fields, executes them in dependency order
     * with variable substitution between steps.
     */
    public List<AnalyzeResponse.QueryResult> executeAll(
            List<DataRequest> requests,
            List<Long> connectionIds,
            User user) {

        boolean hasSteps = requests.stream().anyMatch(r -> r.getStep() != null);

        if (hasSteps) {
            return executeWithChaining(requests, connectionIds, user);
        } else {
            return executeParallel(requests, connectionIds, user);
        }
    }

    // ── Chained (step-ordered) execution ─────────────────────────────────

    private List<AnalyzeResponse.QueryResult> executeWithChaining(
            List<DataRequest> requests,
            List<Long> connectionIds,
            User user) {

        List<AnalyzeResponse.QueryResult> allResults = new ArrayList<>();
        Map<String, String> variables = new HashMap<>(); // $var → resolved value

        // Sort by step
        List<DataRequest> ordered = requests.stream()
                .sorted(Comparator.comparingInt(r -> r.getStep() != null ? r.getStep() : Integer.MAX_VALUE))
                .toList();

        for (DataRequest request : ordered) {
            // Substitute variables in this request's SQL
            DataRequest resolved = request;
            if (request.getDependsOn() != null && !variables.isEmpty()) {
                String originalSql = getRequestSql(request);
                if (originalSql != null) {
                    String resolvedSql = QueryPlanExecutor.replaceVariables(originalSql, variables);
                    resolved = rebuildWithSql(request, resolvedSql);
                    log.info("Step {} resolved: {} → {}", request.getStep(), originalSql, resolvedSql);
                }
            }

            // Find the connection for this request
            Long connectionId = resolveConnectionId(resolved, connectionIds);
            if (connectionId == null) {
                allResults.add(createErrorResult("No matching connection found for sourceId: "
                        + resolved.getSourceId(), null));
                continue;
            }

            DatabaseConnection connection = connectionRepository.findByIdAndUserId(connectionId, user.getId());
            if (connection == null) {
                allResults.add(createErrorResult("Connection not found", connectionId));
                continue;
            }

            DataSource dataSource = dataSourceRegistry.getDataSource(connection);
            if (dataSource == null || !dataSource.isAvailable()) {
                allResults.add(createErrorResult("Data source not available", connectionId));
                continue;
            }

            // Execute
            AnalyzeResponse.QueryResult result = executeRequest(dataSource, resolved, connection);
            allResults.add(result);

            // Extract output variable if defined
            if (request.getOutputAs() != null && request.getOutputField() != null && result.getData() != null) {
                String value = QueryPlanExecutor.extractOutputValue(result.getData(), request.getOutputField());
                if (value != null) {
                    variables.put(request.getOutputAs(), value);
                    log.info("Step {} extracted {} = {}", request.getStep(), request.getOutputAs(), value);
                } else {
                    log.warn("Step {} could not extract {} from field '{}'",
                            request.getStep(), request.getOutputAs(), request.getOutputField());
                }
            }
        }

        return allResults;
    }

    // ── Parallel (no-dependency) execution ───────────────────────────────

    private List<AnalyzeResponse.QueryResult> executeParallel(
            List<DataRequest> requests,
            List<Long> connectionIds,
            User user) {

        List<AnalyzeResponse.QueryResult> results = new ArrayList<>();
        Map<Long, List<DataRequest>> requestsByConnection = groupByConnection(requests, connectionIds);

        for (Map.Entry<Long, List<DataRequest>> entry : requestsByConnection.entrySet()) {
            Long connectionId = entry.getKey();
            List<DataRequest> connectionRequests = entry.getValue();

            DatabaseConnection connection = connectionRepository.findByIdAndUserId(connectionId, user.getId());
            if (connection == null) {
                log.warn("Connection {} not found for user {}", connectionId, user.getId());
                results.add(createErrorResult("Connection not found", connectionId));
                continue;
            }

            DataSource dataSource = dataSourceRegistry.getDataSource(connection);
            if (dataSource == null || !dataSource.isAvailable()) {
                log.warn("Data source {} is not available", connectionId);
                results.add(createErrorResult("Data source not available", connectionId));
                continue;
            }

            for (DataRequest request : connectionRequests) {
                results.add(executeRequest(dataSource, request, connection));
            }
        }

        return results;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private AnalyzeResponse.QueryResult executeRequest(
            DataSource dataSource,
            DataRequest request,
            DatabaseConnection connection) {

        long startTime = System.currentTimeMillis();

        try {
            log.info("Executing {} on {}", request.getRequestType(), dataSource.getName());

            ExecutionResult result = dataSource.execute(request);
            long executionTime = System.currentTimeMillis() - startTime;

            if (result.isSuccess()) {
                return AnalyzeResponse.QueryResult.builder()
                        .connectionId(connection.getId())
                        .connectionName(connection.getName())
                        .data(result.getData())
                        .columns(result.getColumns())
                        .rowCount(result.getRowCount())
                        .executionTimeMs(executionTime)
                        .explanation(request.getDescription())
                        .build();
            } else {
                return AnalyzeResponse.QueryResult.builder()
                        .connectionId(connection.getId())
                        .connectionName(connection.getName())
                        .errorMessage(result.getErrorMessage())
                        .explanation(request.getDescription())
                        .executionTimeMs(executionTime)
                        .build();
            }

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Failed to execute request: {}", e.getMessage(), e);

            return AnalyzeResponse.QueryResult.builder()
                    .connectionId(connection.getId())
                    .connectionName(connection.getName())
                    .errorMessage("Execution failed: " + e.getMessage())
                    .explanation(request.getDescription())
                    .executionTimeMs(executionTime)
                    .build();
        }
    }

    /**
     * Resolve the connection ID for a request.
     * Uses explicit sourceId if available, otherwise falls back to first
     * connection.
     */
    private Long resolveConnectionId(DataRequest request, List<Long> connectionIds) {
        if (request.getSourceId() != null) {
            try {
                Long sourceId = Long.parseLong(request.getSourceId());
                if (connectionIds.contains(sourceId)) {
                    return sourceId;
                }
                log.warn("sourceId {} not in provided connectionIds {}", sourceId, connectionIds);
            } catch (NumberFormatException e) {
                log.warn("Invalid sourceId format: {}", request.getSourceId());
            }
        }
        // Fallback to first connection
        return connectionIds != null && !connectionIds.isEmpty() ? connectionIds.get(0) : null;
    }

    private String getRequestSql(DataRequest request) {
        if (request instanceof com.datanexus.datanexus.service.datasource.request.SqlQuery sq) {
            return sq.getSql();
        }
        return null;
    }

    private DataRequest rebuildWithSql(DataRequest request, String newSql) {
        if (request instanceof com.datanexus.datanexus.service.datasource.request.SqlQuery sq) {
            return com.datanexus.datanexus.service.datasource.request.SqlQuery.builder()
                    .sql(newSql)
                    .explanation(sq.getExplanation())
                    .sourceId(sq.getSourceId())
                    .step(sq.getStep())
                    .dependsOn(sq.getDependsOn())
                    .outputAs(sq.getOutputAs())
                    .outputField(sq.getOutputField())
                    .build();
        }
        return request;
    }

    /**
     * Group requests by their target connection (legacy mode without sourceId).
     */
    private Map<Long, List<DataRequest>> groupByConnection(
            List<DataRequest> requests,
            List<Long> connectionIds) {

        Map<Long, List<DataRequest>> grouped = new HashMap<>();

        if (connectionIds == null || connectionIds.isEmpty()) {
            log.warn("No connection IDs provided");
            return grouped;
        }

        for (DataRequest request : requests) {
            Long targetConnection = resolveConnectionId(request, connectionIds);
            if (targetConnection != null) {
                grouped.computeIfAbsent(targetConnection, k -> new ArrayList<>()).add(request);
            }
        }

        return grouped;
    }

    private AnalyzeResponse.QueryResult createErrorResult(String errorMessage, Long connectionId) {
        return AnalyzeResponse.QueryResult.builder()
                .connectionId(connectionId)
                .errorMessage(errorMessage)
                .build();
    }
}

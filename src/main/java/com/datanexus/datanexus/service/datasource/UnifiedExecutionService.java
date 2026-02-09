package com.datanexus.datanexus.service.datasource;

import com.datanexus.datanexus.dto.websocket.AnalyzeResponse;
import com.datanexus.datanexus.entity.DatabaseConnection;
import com.datanexus.datanexus.entity.User;
import com.datanexus.datanexus.repository.DatabaseConnectionRepository;
import com.datanexus.datanexus.service.datasource.request.MCPResourceRead;
import com.datanexus.datanexus.service.datasource.request.MCPToolCall;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified execution service for all types of data requests
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnifiedExecutionService {

    private final DataSourceRegistry dataSourceRegistry;
    private final DatabaseConnectionRepository connectionRepository;

    /**
     * Execute all data requests and return formatted results
     */
    public List<AnalyzeResponse.QueryResult> executeAll(
            List<DataRequest> requests,
            List<Long> connectionIds,
            User user) {

        List<AnalyzeResponse.QueryResult> results = new ArrayList<>();

        // Group requests by connection for efficiency
        Map<Long, List<DataRequest>> requestsByConnection = groupByConnection(requests, connectionIds);

        for (Map.Entry<Long, List<DataRequest>> entry : requestsByConnection.entrySet()) {
            Long connectionId = entry.getKey();
            List<DataRequest> connectionRequests = entry.getValue();

            // Get connection and data source
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

            // Execute each request for this connection
            for (DataRequest request : connectionRequests) {
                results.add(executeRequest(dataSource, request, connection));
            }
        }

        return results;
    }

    /**
     * Execute a single data request
     */
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
     * Group requests by their target connection
     * For now, we'll use a simple heuristic: match by request type to connection
     * type
     */
    private Map<Long, List<DataRequest>> groupByConnection(
            List<DataRequest> requests,
            List<Long> connectionIds) {

        Map<Long, List<DataRequest>> grouped = new HashMap<>();

        if (connectionIds == null || connectionIds.isEmpty()) {
            log.warn("No connection IDs provided");
            return grouped;
        }

        // Simple strategy: assign all SQL to first connection, MCP to rest
        // TODO: Improve this with explicit sourceId in DataRequest
        Long firstConnection = connectionIds.get(0);

        for (DataRequest request : requests) {
            Long targetConnection = firstConnection;

            // Try to match request type to connection type
            if (request instanceof MCPToolCall || request instanceof MCPResourceRead) {
                // Find an MCP connection
                for (Long connId : connectionIds) {
                    // Would need to check connection type, for now use second connection if
                    // available
                    if (connectionIds.size() > 1 && !connId.equals(firstConnection)) {
                        targetConnection = connId;
                        break;
                    }
                }
            }

            grouped.computeIfAbsent(targetConnection, k -> new ArrayList<>()).add(request);
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

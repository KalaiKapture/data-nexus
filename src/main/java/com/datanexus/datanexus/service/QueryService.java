package com.datanexus.datanexus.service;

import com.datanexus.datanexus.dto.query.ExecuteQueryRequest;
import com.datanexus.datanexus.dto.query.QueryResultDto;
import com.datanexus.datanexus.dto.query.QuerySuggestionsRequest;
import com.datanexus.datanexus.entity.DatabaseConnection;
import com.datanexus.datanexus.entity.User;
import com.datanexus.datanexus.exception.ApiException;
import com.datanexus.datanexus.repository.DatabaseConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class QueryService {

    private final DatabaseConnectionRepository connectionRepository;

    public QueryResultDto executeQuery(ExecuteQueryRequest request, User user) {
        if (request.getConnectionIds().isEmpty()) {
            throw ApiException.badRequest("BAD_REQUEST", "At least one connection ID is required");
        }

        String connectionId = request.getConnectionIds().get(0);
        DatabaseConnection conn = connectionRepository.findByIdAndUserId(connectionId, user.getId())
                .orElseThrow(() -> ApiException.notFound("CONNECTION_NOT_FOUND", "Database connection not found"));

        String jdbcUrl = buildJdbcUrl(conn.getType(), conn.getHost(), conn.getPort(), conn.getDatabase());
        long startTime = System.currentTimeMillis();

        try (Connection dbConn = DriverManager.getConnection(jdbcUrl, conn.getUsername(), conn.getPassword());
             Statement stmt = dbConn.createStatement()) {

            stmt.setQueryTimeout(30);
            ResultSet rs = stmt.executeQuery(request.getQuery());
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columns.add(metaData.getColumnLabel(i));
            }

            List<Map<String, Object>> data = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(columns.get(i - 1), rs.getObject(i));
                }
                data.add(row);
            }
            rs.close();

            long executionTime = System.currentTimeMillis() - startTime;

            return QueryResultDto.builder()
                    .columns(columns)
                    .data(data)
                    .totalRows(data.size())
                    .executionTime(executionTime)
                    .generatedSQL(request.getQuery())
                    .sourceConnections(List.of(
                            QueryResultDto.SourceConnection.builder()
                                    .id(conn.getId())
                                    .name(conn.getName())
                                    .build()
                    ))
                    .build();

        } catch (SQLException e) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "QUERY_EXECUTION_ERROR",
                    "Failed to execute query: " + e.getMessage()
            );
        }
    }

    public List<String> getQuerySuggestions(QuerySuggestionsRequest request, User user) {
        return List.of(
                "Show me total revenue by month",
                "List top 10 customers by order value",
                "What's the average order value this quarter?",
                "Show sales breakdown by product category"
        );
    }

    private String buildJdbcUrl(String type, String host, String port, String database) {
        return switch (type.toLowerCase()) {
            case "postgresql" -> "jdbc:postgresql://" + host + ":" + port + "/" + database;
            case "mysql" -> "jdbc:mysql://" + host + ":" + port + "/" + database;
            default -> "jdbc:" + type + "://" + host + ":" + port + "/" + database;
        };
    }
}

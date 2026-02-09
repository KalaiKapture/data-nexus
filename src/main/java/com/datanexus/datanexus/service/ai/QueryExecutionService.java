package com.datanexus.datanexus.service.ai;

import com.datanexus.datanexus.entity.DatabaseConnection;
import com.datanexus.datanexus.util.JdbcUrlBuilder;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryExecutionService {

    private final QuerySafetyValidator safetyValidator;

    @Getter
    @Builder
    public static class ExecutionResult {
        private boolean success;
        private List<Map<String, Object>> data;
        private List<String> columns;
        private int rowCount;
        private long executionTimeMs;
        private String errorMessage;
    }

    public ExecutionResult execute(DatabaseConnection conn, String sql) {
        QuerySafetyValidator.ValidationResult validation = safetyValidator.validate(sql);
        if (!validation.isValid()) {
            return ExecutionResult.builder()
                    .success(false)
                    .errorMessage(validation.getReason())
                    .build();
        }

        String jdbcUrl = JdbcUrlBuilder.buildUrl(conn.getType(), conn.getHost(), conn.getPort(), conn.getDatabase());
        long startTime = System.currentTimeMillis();

        try (Connection dbConn = DriverManager.getConnection(jdbcUrl, conn.getUsername(), conn.getPassword())) {
            dbConn.setReadOnly(true);
            dbConn.setAutoCommit(false);

            try (PreparedStatement stmt = dbConn.prepareStatement(sql)) {
                stmt.setQueryTimeout(30);

                try (ResultSet rs = stmt.executeQuery()) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();

                    List<String> columns = new ArrayList<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String label = metaData.getColumnLabel(i);
                        columns.add(label != null ? label : metaData.getColumnName(i));
                    }

                    List<Map<String, Object>> data = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            Object value = rs.getObject(i);
                            row.put(columns.get(i - 1), formatValue(value));
                        }
                        data.add(row);
                    }

                    long executionTime = System.currentTimeMillis() - startTime;

                    return ExecutionResult.builder()
                            .success(true)
                            .data(data)
                            .columns(columns)
                            .rowCount(data.size())
                            .executionTimeMs(executionTime)
                            .build();
                }
            } finally {
                dbConn.rollback();
            }
        } catch (SQLException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Query execution failed on connection {}: {}", conn.getId(), e.getMessage());

            return ExecutionResult.builder()
                    .success(false)
                    .executionTimeMs(executionTime)
                    .errorMessage(sanitizeErrorMessage(e.getMessage()))
                    .build();
        }
    }

    private Object formatValue(Object value) {
        if (value == null)
            return null;
        if (value instanceof java.sql.Timestamp ts)
            return ts.toInstant().toString();
        if (value instanceof java.sql.Date d)
            return d.toString();
        if (value instanceof java.sql.Time t)
            return t.toString();
        if (value instanceof byte[])
            return "[binary data]";
        return value;
    }

    private String sanitizeErrorMessage(String message) {
        if (message == null)
            return "Unknown database error";
        return message
                .replaceAll("password=\\S+", "password=***")
                .replaceAll("jdbc:[^\\s]+", "[connection-url]");
    }

}

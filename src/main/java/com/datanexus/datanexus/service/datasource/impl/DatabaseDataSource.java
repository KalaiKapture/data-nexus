package com.datanexus.datanexus.service.datasource.impl;

import com.datanexus.datanexus.entity.DatabaseConnection;
import com.datanexus.datanexus.service.ai.SchemaService;
import com.datanexus.datanexus.service.ai.QueryExecutionService;
import com.datanexus.datanexus.service.datasource.*;
import com.datanexus.datanexus.service.datasource.request.SqlQuery;
import com.datanexus.datanexus.service.datasource.schema.SourceSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Database implementation of DataSource
 */
@Slf4j
@RequiredArgsConstructor
public class DatabaseDataSource implements DataSource {

    private final DatabaseConnection connection;
    private final SchemaService schemaService;
    private final QueryExecutionService executionService;

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
        return DataSourceType.DATABASE;
    }

    @Override
    public SourceSchema extractSchema() {
        log.info("Extracting schema for database: {}", connection.getName());
        SchemaService.DatabaseSchema dbSchema = schemaService.extractSchema(connection);
        return SourceSchema.fromDatabase(
                connection.getId().toString(),
                connection.getName(),
                dbSchema);
    }

    @Override
    public ExecutionResult execute(DataRequest request) {
        if (!(request instanceof SqlQuery)) {
            throw new IllegalArgumentException(
                    "DatabaseDataSource only supports SqlQuery requests");
        }

        SqlQuery sqlQuery = (SqlQuery) request;
        log.info("Executing SQL query on database: {}", connection.getName());

        QueryExecutionService.ExecutionResult result = executionService.execute(connection, sqlQuery.getSql());

        return ExecutionResult.builder()
                .success(result.isSuccess())
                .data(result.getData())
                .columns(result.getColumns())
                .rowCount(result.getRowCount())
                .executionTimeMs(result.getExecutionTimeMs())
                .errorMessage(result.getErrorMessage())
                .build();
    }

    @Override
    public boolean isAvailable() {
        try {
            // Simple availability check by trying to extract a minimal schema
            schemaService.extractSchema(connection);
            return true;
        } catch (Exception e) {
            log.warn("Database {} is not available: {}", connection.getName(), e.getMessage());
            return false;
        }
    }

    @Override
    public String getConnectionInfo() {
        return String.format("Database: %s (%s@%s:%s/%s)",
                connection.getName(),
                connection.getType(),
                connection.getHost(),
                connection.getPort(),
                connection.getDatabase());
    }

    /**
     * Factory for creating DatabaseDataSource instances
     */
    @Component
    @RequiredArgsConstructor
    public static class Factory {
        private final SchemaService schemaService;
        private final QueryExecutionService executionService;

        public DatabaseDataSource create(DatabaseConnection connection) {
            return new DatabaseDataSource(connection, schemaService, executionService);
        }
    }
}

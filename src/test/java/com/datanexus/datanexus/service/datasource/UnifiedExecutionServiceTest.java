package com.datanexus.datanexus.service.datasource;

import com.datanexus.datanexus.dto.websocket.AnalyzeResponse;
import com.datanexus.datanexus.entity.DatabaseConnection;
import com.datanexus.datanexus.entity.User;
import com.datanexus.datanexus.repository.DatabaseConnectionRepository;
import com.datanexus.datanexus.service.datasource.request.SqlQuery;
import com.datanexus.datanexus.service.datasource.request.MCPToolCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UnifiedExecutionService
 */
@ExtendWith(MockitoExtension.class)
class UnifiedExecutionServiceTest {

    @Mock
    private DataSourceRegistry dataSourceRegistry;

    @Mock
    private DatabaseConnectionRepository connectionRepository;

    @Mock
    private DataSource dataSource;

    private UnifiedExecutionService executionService;
    private User testUser;
    private DatabaseConnection testConnection;

    @BeforeEach
    void setUp() {
        executionService = new UnifiedExecutionService(dataSourceRegistry, connectionRepository);

        testUser = new User();
        testUser.setId(1L);

        testConnection = new DatabaseConnection();
        testConnection.setId(1L);
        testConnection.setName("Test DB");
        testConnection.setType("postgresql");
    }

    @Test
    void testExecuteAll_WithSingleSQLQuery_ReturnsResults() {
        // Arrange
        SqlQuery sqlQuery = SqlQuery.builder()
                .sql("SELECT * FROM users")
                .explanation("Fetch all users")
                .build();

        ExecutionResult mockResult = ExecutionResult.builder()
                .success(true)
                .data(List.of(Map.of("id", 1, "name", "John")))
                .columns(List.of("id", "name"))
                .rowCount(1)
                .build();

        when(connectionRepository.findByIdAndUserId(1L, 1L)).thenReturn(testConnection);
        when(dataSourceRegistry.getDataSource(testConnection)).thenReturn(dataSource);
        when(dataSource.isAvailable()).thenReturn(true);
        when(dataSource.execute(sqlQuery)).thenReturn(mockResult);

        // Act
        List<AnalyzeResponse.QueryResult> results = executionService.executeAll(
                List.of(sqlQuery),
                List.of(1L),
                testUser);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());

        AnalyzeResponse.QueryResult result = results.get(0);
        assertEquals(1L, result.getConnectionId());
        assertEquals("Test DB", result.getConnectionName());
        assertEquals(1, result.getRowCount());
        assertNotNull(result.getExecutionTimeMs());
        assertEquals("Fetch all users", result.getExplanation());
    }

    @Test
    void testExecuteAll_WithConnectionNotFound_ReturnsError() {
        // Arrange
        SqlQuery sqlQuery = SqlQuery.builder()
                .sql("SELECT * FROM users")
                .build();

        when(connectionRepository.findByIdAndUserId(999L, 1L)).thenReturn(null);

        // Act
        List<AnalyzeResponse.QueryResult> results = executionService.executeAll(
                List.of(sqlQuery),
                List.of(999L),
                testUser);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());

        AnalyzeResponse.QueryResult result = results.get(0);
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Connection not found"));
    }

    @Test
    void testExecuteAll_WithExecutionFailure_ReturnsErrorResult() {
        // Arrange
        SqlQuery sqlQuery = SqlQuery.builder()
                .sql("SELECT * FROM invalid_table")
                .explanation("This will fail")
                .build();

        when(connectionRepository.findByIdAndUserId(1L, 1L)).thenReturn(testConnection);
        when(dataSourceRegistry.getDataSource(testConnection)).thenReturn(dataSource);
        when(dataSource.isAvailable()).thenReturn(true);
        when(dataSource.execute(sqlQuery)).thenThrow(new RuntimeException("Table not found"));

        // Act
        List<AnalyzeResponse.QueryResult> results = executionService.executeAll(
                List.of(sqlQuery),
                List.of(1L),
                testUser);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());

        AnalyzeResponse.QueryResult result = results.get(0);
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Execution failed"));
        assertNotNull(result.getExecutionTimeMs());
    }

    @Test
    void testExecuteAll_WithMultipleRequests_GroupsByConnection() {
        // Arrange
        SqlQuery query1 = SqlQuery.builder().sql("SELECT * FROM users").build();
        SqlQuery query2 = SqlQuery.builder().sql("SELECT * FROM orders").build();

        ExecutionResult result1 = ExecutionResult.builder()
                .success(true)
                .data(new ArrayList<>())
                .columns(new ArrayList<>())
                .rowCount(0)
                .build();

        when(connectionRepository.findByIdAndUserId(1L, 1L)).thenReturn(testConnection);
        when(dataSourceRegistry.getDataSource(testConnection)).thenReturn(dataSource);
        when(dataSource.isAvailable()).thenReturn(true);
        when(dataSource.execute(any(DataRequest.class))).thenReturn(result1);

        // Act
        List<AnalyzeResponse.QueryResult> results = executionService.executeAll(
                List.of(query1, query2),
                List.of(1L),
                testUser);

        // Assert
        assertEquals(2, results.size());
        verify(dataSource, times(2)).execute(any(DataRequest.class));
    }
}

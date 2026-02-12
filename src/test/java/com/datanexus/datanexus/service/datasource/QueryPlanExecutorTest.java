package com.datanexus.datanexus.service.datasource;

import com.datanexus.datanexus.service.datasource.request.SqlQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for QueryPlanExecutor - verifying variable substitution
 * and step-ordering logic for cross-database query chaining.
 */
class QueryPlanExecutorTest {

    @Test
    @DisplayName("Numeric variable substitution - should not quote")
    void numericVariableSubstitution() {
        Map<String, String> variables = Map.of("$user_id", "5");
        String sql = "SELECT * FROM activities WHERE user_id = $user_id";

        String result = QueryPlanExecutor.replaceVariables(sql, variables);

        assertEquals("SELECT * FROM activities WHERE user_id = 5", result);
    }

    @Test
    @DisplayName("String variable substitution - should quote")
    void stringVariableSubstitution() {
        Map<String, String> variables = Map.of("$username", "johndoe");
        String sql = "SELECT * FROM activities WHERE username = $username";

        String result = QueryPlanExecutor.replaceVariables(sql, variables);

        assertEquals("SELECT * FROM activities WHERE username = 'johndoe'", result);
    }

    @Test
    @DisplayName("Multiple variables in same SQL")
    void multipleVariables() {
        Map<String, String> variables = Map.of(
                "$user_id", "5",
                "$dept_id", "12");
        String sql = "SELECT * FROM tasks WHERE user_id = $user_id AND dept_id = $dept_id";

        String result = QueryPlanExecutor.replaceVariables(sql, variables);

        assertEquals("SELECT * FROM tasks WHERE user_id = 5 AND dept_id = 12", result);
    }

    @Test
    @DisplayName("SQL injection prevention - single quotes are escaped")
    void sqlInjectionPrevention() {
        Map<String, String> variables = Map.of("$name", "O'Brien");
        String sql = "SELECT * FROM users WHERE name = $name";

        String result = QueryPlanExecutor.replaceVariables(sql, variables);

        assertEquals("SELECT * FROM users WHERE name = 'O''Brien'", result);
    }

    @Test
    @DisplayName("No variables in SQL - returns unchanged")
    void noVariables() {
        Map<String, String> variables = Map.of("$user_id", "5");
        String sql = "SELECT * FROM users";

        String result = QueryPlanExecutor.replaceVariables(sql, variables);

        assertEquals("SELECT * FROM users", result);
    }

    @Test
    @DisplayName("Extract output value from single row result")
    void extractSingleRowValue() {
        List<Map<String, Object>> result = List.of(
                Map.of("id", 5, "username", "johndoe"));

        String value = QueryPlanExecutor.extractOutputValue(result, "id");

        assertEquals("5", value);
    }

    @Test
    @DisplayName("Extract output value from multi-row result")
    void extractMultiRowValues() {
        List<Map<String, Object>> result = List.of(
                new LinkedHashMap<>(Map.of("id", 1)),
                new LinkedHashMap<>(Map.of("id", 2)),
                new LinkedHashMap<>(Map.of("id", 3)));

        String value = QueryPlanExecutor.extractOutputValue(result, "id");

        assertEquals("1, 2, 3", value);
    }

    @Test
    @DisplayName("Multi-row values in IN clause substitution")
    void multiRowInClause() {
        // Simulate: step 1 returns multiple IDs, step 2 uses them in WHERE IN
        String multiValues = "1, 2, 3";
        Map<String, String> variables = Map.of("$order_ids", multiValues);
        String sql = "SELECT * FROM order_items WHERE order_id IN ($order_ids)";

        String result = QueryPlanExecutor.replaceVariables(sql, variables);

        assertEquals("SELECT * FROM order_items WHERE order_id IN (1, 2, 3)", result);
    }

    @Test
    @DisplayName("Extract value with case-insensitive field match")
    void caseInsensitiveFieldMatch() {
        List<Map<String, Object>> result = List.of(
                Map.of("ID", 42));

        String value = QueryPlanExecutor.extractOutputValue(result, "id");

        assertEquals("42", value);
    }

    @Test
    @DisplayName("hasDependency returns true when dependsOn is set")
    void hasDependencyTrue() {
        SqlQuery query = SqlQuery.builder()
                .sql("SELECT 1")
                .step(2)
                .dependsOn(1)
                .build();

        assertTrue(QueryPlanExecutor.hasDependency(query));
    }

    @Test
    @DisplayName("hasDependency returns false when dependsOn is null")
    void hasDependencyFalse() {
        SqlQuery query = SqlQuery.builder()
                .sql("SELECT 1")
                .step(1)
                .build();

        assertFalse(QueryPlanExecutor.hasDependency(query));
    }

    @Test
    @DisplayName("Empty or null inputs handled gracefully")
    void nullSafety() {
        assertNull(QueryPlanExecutor.extractOutputValue(null, "id"));
        assertNull(QueryPlanExecutor.extractOutputValue(List.of(), "id"));
        assertNull(QueryPlanExecutor.extractOutputValue(List.of(Map.of("a", 1)), null));
        assertEquals("test", QueryPlanExecutor.replaceVariables("test", (Map<String, String>) null));
        assertNull(QueryPlanExecutor.replaceVariables(null, Map.of("$x", "1")));
    }
}

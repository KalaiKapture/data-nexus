package com.datanexus.datanexus.service.datasource;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Executes a plan of DataRequests in step order, resolving $variable
 * placeholders from earlier step results.
 * <p>
 * Example flow:
 * 
 * <pre>
 *   Step 1: SELECT id FROM users WHERE username='johndoe'
 *           → outputAs="$user_id", outputField="id" → extracts value 5
 *   Step 2 (dependsOn=1): SELECT * FROM activities WHERE user_id = $user_id
 *           → SQL becomes: "...WHERE user_id = 5"
 * </pre>
 * 
 * No data is sent back to the AI — substitution happens entirely in Java.
 */
@Slf4j
public class QueryPlanExecutor {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$[a-zA-Z_][a-zA-Z0-9_]*");

    /**
     * Resolve variable placeholders in a list of DataRequests based on
     * execution results from earlier steps.
     *
     * @param requests    all data requests (may contain step/dependsOn)
     * @param stepResults map of step number → execution result data
     * @return ordered list of requests with variables substituted
     */
    public static List<DataRequest> resolveAndOrder(List<DataRequest> requests,
            Map<Integer, List<Map<String, Object>>> stepResults) {
        // If no steps defined, return as-is (backward compatible)
        boolean hasSteps = requests.stream().anyMatch(r -> r.getStep() != null);
        if (!hasSteps) {
            return requests;
        }

        // Sort by step number
        return requests.stream()
                .sorted(Comparator.comparingInt(r -> r.getStep() != null ? r.getStep() : Integer.MAX_VALUE))
                .map(req -> substituteVariables(req, stepResults))
                .collect(Collectors.toList());
    }

    /**
     * Check if a request has unresolved dependencies.
     */
    public static boolean hasDependency(DataRequest request) {
        return request.getDependsOn() != null;
    }

    /**
     * Extract the output variable value from a step's result.
     *
     * @param result      query result rows
     * @param outputField column name to extract
     * @return the extracted value as a string, or null
     */
    public static String extractOutputValue(List<Map<String, Object>> result, String outputField) {
        if (result == null || result.isEmpty() || outputField == null) {
            return null;
        }

        // Take the first row's value for the output field
        Object value = result.get(0).get(outputField);
        if (value == null) {
            // Try case-insensitive match
            for (Map.Entry<String, Object> entry : result.get(0).entrySet()) {
                if (entry.getKey().equalsIgnoreCase(outputField)) {
                    value = entry.getValue();
                    break;
                }
            }
        }

        if (value == null) {
            log.warn("Output field '{}' not found in result columns: {}",
                    outputField, result.get(0).keySet());
            return null;
        }

        // Handle multiple rows: collect all values as comma-separated for IN clauses
        if (result.size() > 1) {
            List<String> values = new ArrayList<>();
            for (Map<String, Object> row : result) {
                Object v = row.get(outputField);
                if (v != null) {
                    values.add(v.toString());
                }
            }
            if (!values.isEmpty()) {
                return String.join(", ", values);
            }
        }

        return value.toString();
    }

    /**
     * Substitute $variable placeholders in a DataRequest's SQL with actual values.
     * Uses resolveAndOrder's variables map for substitution.
     */
    private static DataRequest substituteVariables(DataRequest request,
            Map<Integer, List<Map<String, Object>>> stepResults) {
        if (request.getDependsOn() == null || stepResults == null) {
            return request;
        }
        // This method is kept for potential future use in resolveAndOrder.
        // Currently, substitution is handled by
        // UnifiedExecutionService.executeWithChaining()
        return request;
    }

    /**
     * Replace $variable placeholders using a pre-built variable map.
     *
     * @param sql       SQL containing $variable placeholders
     * @param variables map of variable name (with $) → resolved value
     * @return SQL with variables replaced
     */
    public static String replaceVariables(String sql, Map<String, String> variables) {
        if (sql == null || variables == null || variables.isEmpty()) {
            return sql;
        }

        String result = sql;
        Matcher matcher = VARIABLE_PATTERN.matcher(sql);
        while (matcher.find()) {
            String varName = matcher.group();
            String value = variables.get(varName);
            if (value != null) {
                // Quote the value if it's not purely numeric
                String replacement;
                if (value.matches("-?\\d+(\\.\\d+)?")) {
                    replacement = value;
                } else if (value.contains(", ")) {
                    // Multiple values — wrap each in quotes for IN clause
                    replacement = Arrays.stream(value.split(", "))
                            .map(v -> v.matches("-?\\d+(\\.\\d+)?") ? v : "'" + v.replace("'", "''") + "'")
                            .collect(Collectors.joining(", "));
                } else {
                    replacement = "'" + value.replace("'", "''") + "'";
                }
                result = result.replace(varName, replacement);
            }
        }

        return result;
    }
}

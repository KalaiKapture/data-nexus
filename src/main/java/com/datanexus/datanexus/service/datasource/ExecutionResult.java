package com.datanexus.datanexus.service.datasource;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Result of executing a data request (query, tool call, etc.)
 */
@Getter
@Builder
public class ExecutionResult {
    private boolean success;
    private List<Map<String, Object>> data;
    private List<String> columns;
    private int rowCount;
    private long executionTimeMs;
    private String errorMessage;
    private Object metadata; // Additional source-specific metadata
}

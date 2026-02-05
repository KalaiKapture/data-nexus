package com.datanexus.datanexus.dto.query;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueryResultDto {

    private String type;
    private List<String> columns;
    private List<Map<String, Object>> data;
    private String chartType;
    private int totalRows;
    private long executionTime;
    private String generatedSQL;
    private List<SourceConnection> sourceConnections;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SourceConnection {
        private String id;
        private String name;
    }
}

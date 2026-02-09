package com.datanexus.datanexus.dto.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnalyzeResponse {

    private boolean success;
    private Long conversationId;
    private String summary;
    private List<QueryResult> queryResults;
    private String suggestedVisualization;
    private ErrorInfo error;
    private Instant timestamp;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QueryResult {
        private Long connectionId;
        private String connectionName;
        private String query;
        private List<Map<String, Object>> data;
        private List<String> columns;
        private int rowCount;
        private String explanation;
        private String errorMessage;
        private Long executionTimeMs;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ErrorInfo {
        private String code;
        private String message;
        private String suggestion;
    }

    public static AnalyzeResponse success(Long conversationId, String summary,
            List<QueryResult> queryResults,
            String suggestedVisualization) {
        return AnalyzeResponse.builder()
                .success(true)
                .conversationId(conversationId)
                .summary(summary)
                .queryResults(queryResults)
                .suggestedVisualization(suggestedVisualization)
                .timestamp(Instant.now())
                .build();
    }

    public static AnalyzeResponse error(Long conversationId, String code,
            String message, String suggestion) {
        return AnalyzeResponse.builder()
                .success(false)
                .conversationId(conversationId)
                .error(ErrorInfo.builder()
                        .code(code)
                        .message(message)
                        .suggestion(suggestion)
                        .build())
                .timestamp(Instant.now())
                .build();
    }
}

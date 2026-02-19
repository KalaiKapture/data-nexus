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

    /**
     * Discriminator so the frontend knows what kind of system response this is.
     * <ul>
     *   <li>QUERY_RESULT  – query executed, raw data rows attached</li>
     *   <li>ANALYSIS      – AI text analysis of the data</li>
     *   <li>UI_DASHBOARD  – AI-generated standalone HTML dashboard</li>
     *   <li>ERROR         – something went wrong</li>
     * </ul>
     */
    public enum ResponseType { QUERY_RESULT, ANALYSIS, UI_DASHBOARD, ERROR }

    private ResponseType responseType;
    private boolean success;
    private Long conversationId;
    private String summary;
    private List<QueryResult> queryResults;
    private String suggestedVisualization;
    private ErrorInfo error;
    private Instant timestamp;

    // AI-generated data analysis (markdown/text) — present in ANALYSIS responses
    private String analysis;

    // AI-generated standalone HTML dashboard — present in UI_DASHBOARD responses
    private String dashboardHtml;

    // Unique ID for downloading the dashboard — present in UI_DASHBOARD responses
    private String dashboardId;

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

    // ── Response 1: query results (raw data) ─────────────────────────────────

    public static AnalyzeResponse queryResult(Long conversationId, String summary,
            List<QueryResult> queryResults) {
        return AnalyzeResponse.builder()
                .responseType(ResponseType.QUERY_RESULT)
                .success(true)
                .conversationId(conversationId)
                .summary(summary)
                .queryResults(queryResults)
                .timestamp(Instant.now())
                .build();
    }

    // ── Response 2: AI analysis text ─────────────────────────────────────────

    public static AnalyzeResponse analysisResponse(Long conversationId, String analysis) {
        return AnalyzeResponse.builder()
                .responseType(ResponseType.ANALYSIS)
                .success(true)
                .conversationId(conversationId)
                .analysis(analysis)
                .timestamp(Instant.now())
                .build();
    }

    // ── Response 3: HTML dashboard ────────────────────────────────────────────

    public static AnalyzeResponse dashboardResponse(Long conversationId,
            String dashboardHtml, String dashboardId) {
        return AnalyzeResponse.builder()
                .responseType(ResponseType.UI_DASHBOARD)
                .success(true)
                .conversationId(conversationId)
                .dashboardHtml(dashboardHtml)
                .dashboardId(dashboardId)
                .timestamp(Instant.now())
                .build();
    }

    // ── Legacy / no-data ─────────────────────────────────────────────────────

    /** Used when queries returned no data rows — sends results only, no analysis. */
    public static AnalyzeResponse success(Long conversationId, String summary,
            List<QueryResult> queryResults,
            String suggestedVisualization) {
        return AnalyzeResponse.builder()
                .responseType(ResponseType.QUERY_RESULT)
                .success(true)
                .conversationId(conversationId)
                .summary(summary)
                .queryResults(queryResults)
                .suggestedVisualization(suggestedVisualization)
                .timestamp(Instant.now())
                .build();
    }

    // ── Error ─────────────────────────────────────────────────────────────────

    public static AnalyzeResponse error(Long conversationId, String code,
            String message, String suggestion) {
        return AnalyzeResponse.builder()
                .responseType(ResponseType.ERROR)
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

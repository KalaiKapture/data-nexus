package com.datanexus.datanexus.service.ai;

import com.datanexus.datanexus.dto.websocket.AnalyzeResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

// Set is already covered by java.util.* but explicit for clarity

/**
 * Builds a compact structural summary of query result data to send to the AI
 * instead of the raw rows.
 *
 * <p>Benefits over sending raw data:
 * <ul>
 *   <li>Drastically fewer tokens (structure + stats vs. thousands of rows)</li>
 *   <li>No risk of token-limit errors on large result sets</li>
 *   <li>AI still has everything it needs: column names, types, value ranges,
 *       top values, nullability — sufficient to write analysis and chart code</li>
 * </ul>
 *
 * <p>The actual row data is embedded server-side into the generated HTML as a
 * JS {@code const datasets = [...]} variable, so charts and tables still show
 * real numbers without the AI ever seeing the rows.
 */
@Slf4j
public class DataSummarizer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Max distinct top-values to include per column */
    private static final int MAX_TOP_VALUES = 10;
    /** Max sample rows to include as concrete examples */
    private static final int SAMPLE_ROWS = 5;

    /**
     * Column name patterns that are considered sensitive.
     * Matching columns are excluded from top-values, sample rows, and
     * any content sent to the AI — only their name, type, and null-count
     * are reported so the AI understands the schema without seeing values.
     */
    private static final Set<String> SENSITIVE_PATTERNS = Set.of(
            "password", "passwd", "pwd", "secret", "token", "apikey", "api_key",
            "access_key", "private_key", "salt", "hash",
            "ssn", "social_security", "national_id",
            "credit_card", "card_number", "cvv", "card_no",
            "bank_account", "account_number", "routing_number",
            "email", "phone", "mobile", "contact",
            "address", "street", "zipcode", "zip_code",
            "passport", "license", "driving_license",
            "dob", "date_of_birth", "birth_date"
    );

    private DataSummarizer() {}

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Build a human-readable structural summary string for use inside an AI prompt.
     * No raw data rows are included — only column profiles and sample rows.
     */
    public static String buildStructureSummary(List<AnalyzeResponse.QueryResult> queryResults) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < queryResults.size(); i++) {
            AnalyzeResponse.QueryResult qr = queryResults.get(i);
            List<Map<String, Object>> data = qr.getData();
            List<String> columns = qr.getColumns() != null ? qr.getColumns() : List.of();

            sb.append("--- Dataset ").append(i + 1).append(" ---\n");
            if (qr.getQuery() != null)         sb.append("Query:       ").append(qr.getQuery()).append("\n");
            if (qr.getExplanation() != null)   sb.append("Purpose:     ").append(qr.getExplanation()).append("\n");
            sb.append("Columns:     ").append(columns.isEmpty() ? "N/A" : String.join(", ", columns)).append("\n");
            sb.append("Total rows:  ").append(qr.getRowCount()).append("\n");

            if (qr.getErrorMessage() != null) {
                sb.append("ERROR: ").append(qr.getErrorMessage()).append("\n\n");
                continue;
            }

            if (data == null || data.isEmpty()) {
                sb.append("(no rows returned)\n\n");
                continue;
            }

            // Column profiles
            sb.append("\nColumn profiles:\n");
            for (String col : columns) {
                boolean sensitive = isSensitiveColumn(col);
                if (sensitive) {
                    // Only report name + type for sensitive columns — never values
                    sb.append("  ").append(col).append(":\n");
                    sb.append("    type:       [REDACTED — sensitive column, values not shared]\n");
                    continue;
                }
                ColumnProfile profile = profileColumn(col, data);
                sb.append("  ").append(col).append(":\n");
                sb.append("    type:       ").append(profile.inferredType).append("\n");
                sb.append("    nulls:      ").append(profile.nullCount)
                        .append(" / ").append(data.size()).append("\n");
                if (profile.isNumeric) {
                    sb.append("    min:        ").append(profile.min).append("\n");
                    sb.append("    max:        ").append(profile.max).append("\n");
                    sb.append("    avg:        ").append(String.format("%.2f", profile.avg)).append("\n");
                    sb.append("    sum:        ").append(String.format("%.2f", profile.sum)).append("\n");
                }
                if (!profile.topValues.isEmpty()) {
                    sb.append("    top values: ").append(profile.topValues).append("\n");
                }
                if (profile.distinctCount >= 0) {
                    sb.append("    distinct:   ").append(profile.distinctCount).append("\n");
                }
            }

            // Sample rows — redact sensitive column values
            int sampleSize = Math.min(SAMPLE_ROWS, data.size());
            sb.append("\nSample rows (first ").append(sampleSize).append(", sensitive columns redacted):\n");
            for (int r = 0; r < sampleSize; r++) {
                Map<String, Object> raw = data.get(r);
                Map<String, Object> redacted = new LinkedHashMap<>();
                for (String col : columns) {
                    redacted.put(col, isSensitiveColumn(col) ? "[REDACTED]" : raw.get(col));
                }
                try {
                    sb.append("  ").append(MAPPER.writeValueAsString(redacted)).append("\n");
                } catch (JsonProcessingException e) {
                    sb.append("  [row ").append(r).append(" serialization error]\n");
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Serialise the actual data rows as a compact JSON array for server-side
     * injection into the generated HTML.  All rows are included here because
     * this string never goes to the AI — it goes straight into the {@code <script>}
     * block of the finished page.
     */
    public static String serializeDataForHtml(List<AnalyzeResponse.QueryResult> queryResults) {
        List<Map<String, Object>> datasets = new ArrayList<>();
        for (AnalyzeResponse.QueryResult qr : queryResults) {
            Map<String, Object> ds = new LinkedHashMap<>();
            ds.put("query",    qr.getQuery());
            ds.put("columns",  qr.getColumns() != null ? qr.getColumns() : List.of());
            ds.put("rowCount", qr.getRowCount());
            ds.put("rows",     qr.getData() != null ? qr.getData() : List.of());
            datasets.add(ds);
        }
        try {
            return MAPPER.writeValueAsString(datasets);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise datasets for HTML injection", e);
            return "[]";
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    /**
     * Returns true if the column name matches any known sensitive pattern.
     * Matching is case-insensitive and checks if the column name contains
     * the pattern as a word segment (e.g. "user_email" matches "email").
     */
    static boolean isSensitiveColumn(String column) {
        String lower = column.toLowerCase().replaceAll("[^a-z0-9]", "_");
        return SENSITIVE_PATTERNS.stream().anyMatch(pattern ->
                lower.equals(pattern) ||
                lower.startsWith(pattern + "_") ||
                lower.endsWith("_" + pattern) ||
                lower.contains("_" + pattern + "_")
        );
    }

    private static ColumnProfile profileColumn(String column, List<Map<String, Object>> rows) {
        ColumnProfile p = new ColumnProfile();
        List<Object> values = rows.stream()
                .map(r -> r.get(column))
                .collect(Collectors.toList());

        p.nullCount = (int) values.stream().filter(Objects::isNull).count();

        List<Object> nonNull = values.stream().filter(Objects::nonNull).collect(Collectors.toList());
        if (nonNull.isEmpty()) {
            p.inferredType = "unknown";
            return p;
        }

        // Try numeric
        List<Double> nums = new ArrayList<>();
        for (Object v : nonNull) {
            try {
                nums.add(Double.parseDouble(v.toString()));
            } catch (NumberFormatException ignored) {}
        }

        if (nums.size() == nonNull.size()) {
            p.isNumeric = true;
            p.inferredType = "numeric";
            p.min = nums.stream().mapToDouble(d -> d).min().orElse(0);
            p.max = nums.stream().mapToDouble(d -> d).max().orElse(0);
            p.avg = nums.stream().mapToDouble(d -> d).average().orElse(0);
            p.sum = nums.stream().mapToDouble(d -> d).sum();
        } else {
            // Detect date-like strings
            boolean looksLikeDate = nonNull.stream().limit(5).allMatch(v -> {
                String s = v.toString();
                return s.matches("\\d{4}-\\d{2}-\\d{2}.*") || s.matches("\\d{2}/\\d{2}/\\d{4}.*");
            });
            p.inferredType = looksLikeDate ? "date" : "string";
        }

        // Top values by frequency
        Map<Object, Long> freq = nonNull.stream()
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()));
        p.distinctCount = freq.size();
        p.topValues = freq.entrySet().stream()
                .sorted(Map.Entry.<Object, Long>comparingByValue().reversed())
                .limit(MAX_TOP_VALUES)
                .map(e -> e.getKey() + " (" + e.getValue() + ")")
                .collect(Collectors.toList());

        return p;
    }

    // ── Internal DTO ─────────────────────────────────────────────────────

    private static class ColumnProfile {
        String inferredType = "string";
        boolean isNumeric = false;
        int nullCount = 0;
        int distinctCount = -1;
        double min, max, avg, sum;
        List<String> topValues = List.of();
    }
}

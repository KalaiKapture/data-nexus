package com.datanexus.datanexus.service.ai;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryGeneratorService {

    private final QuerySafetyValidator safetyValidator;

    private static final int DEFAULT_LIMIT = 100;

    @Getter
    @Builder
    public static class GeneratedQuery {
        private Long connectionId;
        private String sql;
        private String explanation;
        private boolean valid;
        private String validationError;
    }

    public QueryGenerationResult generateQueries(String userMessage,
                                                 Map<Long, SchemaService.DatabaseSchema> schemas) {
        List<GeneratedQuery> queries = new ArrayList<>();

        String intent = analyzeIntent(userMessage);

        for (Map.Entry<Long, SchemaService.DatabaseSchema> entry : schemas.entrySet()) {
            Long connectionId = entry.getKey();
            SchemaService.DatabaseSchema schema = entry.getValue();

            GeneratedQuery query = buildQueryForSchema(userMessage, intent, connectionId, schema);
            queries.add(query);
        }

        return QueryGenerationResult.builder()
                .intent(intent)
                .queries(queries)
                .build();
    }


    @Getter
    @Builder
    public static class QueryGenerationResult {
        private String intent;
        private List<GeneratedQuery> queries;
    }

    private String analyzeIntent(String userMessage) {
        String lower = userMessage.toLowerCase();

        if (containsAny(lower, "count", "how many", "total number", "number of")) {
            return "COUNT";
        }
        if (containsAny(lower, "average", "avg", "mean")) {
            return "AVERAGE";
        }
        if (containsAny(lower, "sum", "total", "combined")) {
            return "SUM";
        }
        if (containsAny(lower, "max", "maximum", "highest", "largest", "most", "top")) {
            return "MAX";
        }
        if (containsAny(lower, "min", "minimum", "lowest", "smallest", "least")) {
            return "MIN";
        }
        if (containsAny(lower, "group by", "grouped", "per", "each", "breakdown", "by category")) {
            return "GROUP";
        }
        if (containsAny(lower, "list", "show", "display", "get", "fetch", "find", "retrieve")) {
            return "LIST";
        }
        if (containsAny(lower, "compare", "difference", "versus", "vs")) {
            return "COMPARE";
        }
        if (containsAny(lower, "trend", "over time", "timeline", "history", "growth")) {
            return "TREND";
        }

        return "LIST";
    }

    private GeneratedQuery buildQueryForSchema(String userMessage, String intent,
                                                Long connectionId,
                                                SchemaService.DatabaseSchema schema) {
        List<SchemaService.TableSchema> matchedTables = findRelevantTables(userMessage, schema);

        if (matchedTables.isEmpty()) {
            return GeneratedQuery.builder()
                    .connectionId(connectionId)
                    .valid(false)
                    .validationError("No relevant tables found in database '" +
                            schema.getConnectionName() + "' for your query.")
                    .build();
        }

        SchemaService.TableSchema primaryTable = matchedTables.get(0);
        List<SchemaService.ColumnSchema> relevantColumns = findRelevantColumns(userMessage, primaryTable);

        String sql = buildSql(intent, primaryTable, relevantColumns, userMessage, schema.getDatabaseType());

        QuerySafetyValidator.ValidationResult validation = safetyValidator.validate(sql);
        if (!validation.isValid()) {
            return GeneratedQuery.builder()
                    .connectionId(connectionId)
                    .sql(sql)
                    .valid(false)
                    .validationError(validation.getReason())
                    .build();
        }

        return GeneratedQuery.builder()
                .connectionId(connectionId)
                .sql(sql)
                .explanation("Querying table '" + primaryTable.getTableName() +
                        "' with " + intent + " operation")
                .valid(true)
                .build();
    }

    private List<SchemaService.TableSchema> findRelevantTables(String userMessage,
                                                                SchemaService.DatabaseSchema schema) {
        String lower = userMessage.toLowerCase();
        List<SchemaService.TableSchema> matched = new ArrayList<>();

        for (SchemaService.TableSchema table : schema.getTables()) {
            String tableLower = table.getTableName().toLowerCase();
            String tableSingular = tableLower.endsWith("s")
                    ? tableLower.substring(0, tableLower.length() - 1) : tableLower;

            if (lower.contains(tableLower) || lower.contains(tableSingular) ||
                lower.contains(tableLower.replace("_", " "))) {
                matched.add(table);
            }
        }

        if (matched.isEmpty() && !schema.getTables().isEmpty()) {
            int bestScore = 0;
            SchemaService.TableSchema bestMatch = null;

            for (SchemaService.TableSchema table : schema.getTables()) {
                int score = 0;
                for (SchemaService.ColumnSchema col : table.getColumns()) {
                    if (lower.contains(col.getName().toLowerCase()) ||
                        lower.contains(col.getName().toLowerCase().replace("_", " "))) {
                        score++;
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = table;
                }
            }

            if (bestMatch != null) {
                matched.add(bestMatch);
            } else {
                matched.add(schema.getTables().get(0));
            }
        }

        return matched;
    }

    private List<SchemaService.ColumnSchema> findRelevantColumns(String userMessage,
                                                                  SchemaService.TableSchema table) {
        String lower = userMessage.toLowerCase();
        List<SchemaService.ColumnSchema> relevant = new ArrayList<>();

        for (SchemaService.ColumnSchema col : table.getColumns()) {
            String colLower = col.getName().toLowerCase();
            if (lower.contains(colLower) || lower.contains(colLower.replace("_", " "))) {
                relevant.add(col);
            }
        }

        if (relevant.isEmpty()) {
            return table.getColumns();
        }

        boolean hasPk = relevant.stream().anyMatch(SchemaService.ColumnSchema::isPrimaryKey);
        if (!hasPk) {
            table.getColumns().stream()
                    .filter(SchemaService.ColumnSchema::isPrimaryKey)
                    .findFirst()
                    .ifPresent(pk -> relevant.add(0, pk));
        }

        return relevant;
    }

    private String buildSql(String intent, SchemaService.TableSchema table,
                             List<SchemaService.ColumnSchema> columns,
                             String userMessage, String dbType) {
        String tableName = quoteIdentifier(table.getTableName(), dbType);
        String alias = "t";

        return switch (intent) {
            case "COUNT" -> buildCountQuery(tableName, alias, columns, userMessage);
            case "AVERAGE" -> buildAggregateQuery("AVG", tableName, alias, columns, userMessage);
            case "SUM" -> buildAggregateQuery("SUM", tableName, alias, columns, userMessage);
            case "MAX" -> buildAggregateQuery("MAX", tableName, alias, columns, userMessage);
            case "MIN" -> buildAggregateQuery("MIN", tableName, alias, columns, userMessage);
            case "GROUP" -> buildGroupQuery(tableName, alias, columns, userMessage);
            case "TREND" -> buildTrendQuery(tableName, alias, columns, userMessage);
            case "COMPARE" -> buildListQuery(tableName, alias, columns);
            default -> buildListQuery(tableName, alias, columns);
        };
    }

    private String buildCountQuery(String tableName, String alias,
                                    List<SchemaService.ColumnSchema> columns,
                                    String userMessage) {
        Optional<SchemaService.ColumnSchema> groupCol = findGroupableColumn(columns, userMessage);

        if (groupCol.isPresent()) {
            String col = groupCol.get().getName();
            return String.format("SELECT %s.%s, COUNT(*) AS count FROM %s %s GROUP BY %s.%s ORDER BY count DESC LIMIT %d",
                    alias, col, tableName, alias, alias, col, DEFAULT_LIMIT);
        }

        return String.format("SELECT COUNT(*) AS total_count FROM %s %s", tableName, alias);
    }

    private String buildAggregateQuery(String function, String tableName, String alias,
                                        List<SchemaService.ColumnSchema> columns,
                                        String userMessage) {
        Optional<SchemaService.ColumnSchema> numericCol = columns.stream()
                .filter(c -> isNumericType(c.getDataType()))
                .findFirst();

        if (numericCol.isPresent()) {
            String col = numericCol.get().getName();
            return String.format("SELECT %s(%s.%s) AS %s_%s FROM %s %s",
                    function, alias, col, function.toLowerCase(), col, tableName, alias);
        }

        return String.format("SELECT COUNT(*) AS total_count FROM %s %s", tableName, alias);
    }

    private String buildGroupQuery(String tableName, String alias,
                                    List<SchemaService.ColumnSchema> columns,
                                    String userMessage) {
        Optional<SchemaService.ColumnSchema> groupCol = findGroupableColumn(columns, userMessage);
        Optional<SchemaService.ColumnSchema> numericCol = columns.stream()
                .filter(c -> isNumericType(c.getDataType()))
                .findFirst();

        if (groupCol.isPresent()) {
            String gCol = groupCol.get().getName();
            String aggPart = numericCol.isPresent()
                    ? String.format("SUM(%s.%s) AS total, AVG(%s.%s) AS average", alias, numericCol.get().getName(), alias, numericCol.get().getName())
                    : "COUNT(*) AS count";

            return String.format("SELECT %s.%s, %s FROM %s %s GROUP BY %s.%s ORDER BY %s DESC LIMIT %d",
                    alias, gCol, aggPart, tableName, alias, alias, gCol,
                    numericCol.isPresent() ? "total" : "count", DEFAULT_LIMIT);
        }

        return buildListQuery(tableName, alias, columns);
    }

    private String buildTrendQuery(String tableName, String alias,
                                    List<SchemaService.ColumnSchema> columns,
                                    String userMessage) {
        Optional<SchemaService.ColumnSchema> dateCol = columns.stream()
                .filter(c -> isDateType(c.getDataType()))
                .findFirst();

        Optional<SchemaService.ColumnSchema> numericCol = columns.stream()
                .filter(c -> isNumericType(c.getDataType()))
                .findFirst();

        if (dateCol.isPresent()) {
            String dCol = dateCol.get().getName();
            String valuePart = numericCol.isPresent()
                    ? String.format("SUM(%s.%s) AS total", alias, numericCol.get().getName())
                    : "COUNT(*) AS count";

            return String.format("SELECT %s.%s, %s FROM %s %s GROUP BY %s.%s ORDER BY %s.%s ASC LIMIT %d",
                    alias, dCol, valuePart, tableName, alias, alias, dCol, alias, dCol, DEFAULT_LIMIT);
        }

        return buildListQuery(tableName, alias, columns);
    }

    private String buildListQuery(String tableName, String alias,
                                   List<SchemaService.ColumnSchema> columns) {
        String colList = columns.stream()
                .map(c -> alias + "." + c.getName())
                .collect(Collectors.joining(", "));

        return String.format("SELECT %s FROM %s %s LIMIT %d", colList, tableName, alias, DEFAULT_LIMIT);
    }

    private Optional<SchemaService.ColumnSchema> findGroupableColumn(
            List<SchemaService.ColumnSchema> columns, String userMessage) {
        String lower = userMessage.toLowerCase();

        return columns.stream()
                .filter(c -> !c.isPrimaryKey() && !isNumericType(c.getDataType()) && !isDateType(c.getDataType()))
                .filter(c -> lower.contains(c.getName().toLowerCase()) ||
                             lower.contains(c.getName().toLowerCase().replace("_", " ")))
                .findFirst()
                .or(() -> columns.stream()
                        .filter(c -> !c.isPrimaryKey() && isStringType(c.getDataType()))
                        .findFirst());
    }

    private boolean isNumericType(String dataType) {
        String upper = dataType.toUpperCase();
        return upper.contains("INT") || upper.contains("DECIMAL") || upper.contains("NUMERIC") ||
               upper.contains("FLOAT") || upper.contains("DOUBLE") || upper.contains("REAL") ||
               upper.contains("MONEY") || upper.contains("SERIAL");
    }

    private boolean isDateType(String dataType) {
        String upper = dataType.toUpperCase();
        return upper.contains("DATE") || upper.contains("TIME") || upper.contains("TIMESTAMP");
    }

    private boolean isStringType(String dataType) {
        String upper = dataType.toUpperCase();
        return upper.contains("CHAR") || upper.contains("VARCHAR") || upper.contains("TEXT") ||
               upper.contains("STRING");
    }

    private String quoteIdentifier(String name, String dbType) {
        if (name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            return name;
        }
        return switch (dbType.toLowerCase()) {
            case "mysql" -> "`" + name + "`";
            default -> "\"" + name + "\"";
        };
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    public String suggestVisualization(String intent, List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) {
            return "table";
        }

        int rowCount = data.size();

        return switch (intent) {
            case "COUNT" -> rowCount == 1 ? "kpi_card" : "bar_chart";
            case "AVERAGE", "SUM", "MAX", "MIN" -> rowCount == 1 ? "kpi_card" : "bar_chart";
            case "GROUP" -> rowCount <= 6 ? "pie_chart" : "bar_chart";
            case "TREND" -> "line_chart";
            case "COMPARE" -> "bar_chart";
            default -> "table";
        };
    }
}

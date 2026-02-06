package com.datanexus.datanexus.service.ai;

import com.datanexus.datanexus.entity.DatabaseConnection;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

@Service
@Slf4j
public class SchemaService {

    @Getter
    @Builder
    public static class TableSchema {
        private String tableName;
        private List<ColumnSchema> columns;
        private List<String> primaryKeys;
    }

    @Getter
    @Builder
    public static class ColumnSchema {
        private String name;
        private String dataType;
        private boolean nullable;
        private boolean primaryKey;
    }

    @Getter
    @Builder
    public static class DatabaseSchema {
        private Long connectionId;
        private String connectionName;
        private String databaseType;
        private List<TableSchema> tables;
    }

    public DatabaseSchema extractSchema(DatabaseConnection conn) {
        String jdbcUrl = buildJdbcUrl(conn.getType(), conn.getHost(), conn.getPort(), conn.getDatabase());
        List<TableSchema> tables = new ArrayList<>();

        try (Connection dbConn = DriverManager.getConnection(jdbcUrl, conn.getUsername(), conn.getPassword())) {
            DatabaseMetaData metaData = dbConn.getMetaData();
            String schema = getDefaultSchema(conn.getType());

            ResultSet tableRs = metaData.getTables(null, schema, "%", new String[]{"TABLE"});

            while (tableRs.next()) {
                String tableName = tableRs.getString("TABLE_NAME");

                if (isSystemTable(tableName, conn.getType())) {
                    continue;
                }

                Set<String> pkColumns = new HashSet<>();
                try (ResultSet pkRs = metaData.getPrimaryKeys(null, schema, tableName)) {
                    while (pkRs.next()) {
                        pkColumns.add(pkRs.getString("COLUMN_NAME"));
                    }
                }

                List<ColumnSchema> columns = new ArrayList<>();
                try (ResultSet colRs = metaData.getColumns(null, schema, tableName, "%")) {
                    while (colRs.next()) {
                        columns.add(ColumnSchema.builder()
                                .name(colRs.getString("COLUMN_NAME"))
                                .dataType(colRs.getString("TYPE_NAME"))
                                .nullable("YES".equals(colRs.getString("IS_NULLABLE")))
                                .primaryKey(pkColumns.contains(colRs.getString("COLUMN_NAME")))
                                .build());
                    }
                }

                tables.add(TableSchema.builder()
                        .tableName(tableName)
                        .columns(columns)
                        .primaryKeys(new ArrayList<>(pkColumns))
                        .build());
            }
            tableRs.close();

        } catch (SQLException e) {
            log.error("Failed to extract schema for connection {}: {}", conn.getId(), e.getMessage());
            throw new RuntimeException("Failed to extract schema: " + e.getMessage());
        }

        return DatabaseSchema.builder()
                .connectionId(conn.getId())
                .connectionName(conn.getName())
                .databaseType(conn.getType())
                .tables(tables)
                .build();
    }

    public String schemaToDescription(DatabaseSchema schema) {
        StringBuilder sb = new StringBuilder();
        sb.append("Database: ").append(schema.getConnectionName())
          .append(" (").append(schema.getDatabaseType()).append(")\n");
        sb.append("Tables:\n");

        for (TableSchema table : schema.getTables()) {
            sb.append("  - ").append(table.getTableName()).append(" (");
            List<String> colDescs = new ArrayList<>();
            for (ColumnSchema col : table.getColumns()) {
                String desc = col.getName() + " " + col.getDataType();
                if (col.isPrimaryKey()) desc += " PK";
                if (!col.isNullable()) desc += " NOT NULL";
                colDescs.add(desc);
            }
            sb.append(String.join(", ", colDescs));
            sb.append(")\n");
        }

        return sb.toString();
    }

    private String getDefaultSchema(String dbType) {
        return switch (dbType.toLowerCase()) {
            case "postgresql" -> "public";
            case "mysql" -> null;
            default -> null;
        };
    }

    private boolean isSystemTable(String tableName, String dbType) {
        String lower = tableName.toLowerCase();
        if (lower.startsWith("pg_") || lower.startsWith("sql_") || lower.startsWith("information_schema")) {
            return true;
        }
        if ("mysql".equalsIgnoreCase(dbType)) {
            return lower.startsWith("mysql.") || lower.startsWith("sys.") ||
                   lower.startsWith("performance_schema.");
        }
        return false;
    }

    private String buildJdbcUrl(String type, String host, String port, String database) {
        return switch (type.toLowerCase()) {
            case "postgresql" -> "jdbc:postgresql://" + host + ":" + port + "/" + database;
            case "mysql" -> "jdbc:mysql://" + host + ":" + port + "/" + database;
            default -> "jdbc:" + type + "://" + host + ":" + port + "/" + database;
        };
    }
}

package com.datanexus.datanexus.util;

import com.datanexus.datanexus.enums.DatabaseType;
import lombok.experimental.UtilityClass;

/**
 * Utility class for building JDBC connection URLs based on database type
 */
@UtilityClass
public class JdbcUrlBuilder {

    /**
     * Build JDBC URL for a database connection
     * 
     * @param type     Database type (e.g., "postgresql", "mysql")
     * @param host     Database host
     * @param port     Database port
     * @param database Database name
     * @return JDBC URL string
     */
    public static String buildUrl(String type, String host, String port, String database) {
        DatabaseType dbType = DatabaseType.fromId(type);

        if (dbType == null) {
            // Fallback for unknown types
            return "jdbc:" + type + "://" + host + ":" + port + "/" + database;
        }

        return buildUrl(dbType, host, port, database, null);
    }

    /**
     * Build JDBC URL using DatabaseType enum
     * 
     * @param dbType   DatabaseType enum
     * @param host     Database host
     * @param port     Database port
     * @param database Database name
     * @param filePath File path for SQLite
     * @return JDBC URL string
     */
    public static String buildUrl(DatabaseType dbType, String host, String port, String database, String filePath) {
        return switch (dbType) {
            case POSTGRESQL -> "jdbc:postgresql://" + host + ":" + port + "/" + database;
            case MYSQL -> "jdbc:mysql://" + host + ":" + port + "/" + database;
            case SQLITE -> "jdbc:sqlite:" + (filePath != null ? filePath : database);
            case SUPABASE -> "jdbc:postgresql://" + host + ":" + port + "/" + database + "?sslmode=require";
            case STARROCKS -> "jdbc:mysql://" + host + ":" + port + "/" + database; // Uses MySQL protocol
            case CLICKHOUSE -> "jdbc:clickhouse://" + host + ":" + port + "/" + database;
            case SNOWFLAKE -> "jdbc:snowflake://" + host; // Snowflake uses account identifier
            case MONGODB -> "mongodb://" + host + ":" + port + "/" + database; // Not JDBC
            case REDIS -> "redis://" + host + ":" + port; // Not JDBC
            case ELASTICSEARCH -> "http://" + host + ":" + port; // Not JDBC
            case BIGQUERY -> "jdbc:bigquery://" + host; // Special BigQuery URL
            case MCP -> host; // MCP uses custom protocol
            default -> "jdbc:" + dbType.getId() + "://" + host + ":" + port + "/" + database;
        };
    }

    /**
     * Check if a database type uses JDBC
     */
    public static boolean isJdbcBased(String type) {
        DatabaseType dbType = DatabaseType.fromId(type);
        return dbType != null && dbType.isSql();
    }

    /**
     * Check if a database type uses JDBC
     */
    public static boolean isJdbcBased(DatabaseType dbType) {
        return dbType.isSql();
    }

    /**
     * Get default port for a database type
     */
    public static String getDefaultPort(String type) {
        DatabaseType dbType = DatabaseType.fromId(type);
        return dbType != null ? dbType.getDefaultPort() : null;
    }
}

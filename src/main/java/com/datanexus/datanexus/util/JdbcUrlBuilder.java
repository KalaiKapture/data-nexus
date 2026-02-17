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
        if (dbType == DatabaseType.POSTGRESQL) {
            return "jdbc:postgresql://" + host + ":" + port + "/" + database;
        } else if (dbType == DatabaseType.MYSQL) {
            return "jdbc:mysql://" + host + ":" + port + "/" + database;
        } else if (dbType == DatabaseType.SQLITE) {
            return "jdbc:sqlite:" + (filePath != null ? filePath : database);
        } else if (dbType == DatabaseType.SUPABASE) {
            return "jdbc:postgresql://" + host + ":" + port + "/" + database + "?sslmode=require";
        } else if (dbType == DatabaseType.STARROCKS) {
            return "jdbc:mysql://" + host + ":" + port + "/" + database;
        } else if (dbType == DatabaseType.CLICKHOUSE) {
            return "jdbc:clickhouse://" + host + ":" + port + "/" + database;
        } else if (dbType == DatabaseType.SNOWFLAKE) {
            return "jdbc:snowflake://" + host;
        } else if (dbType == DatabaseType.MONGODB) {
            return "mongodb://" + host + ":" + port + "/" + database;
        } else if (dbType == DatabaseType.REDIS) {
            return "redis://" + host + ":" + port;
        } else if (dbType == DatabaseType.ELASTICSEARCH) {
            return "http://" + host + ":" + port;
        } else if (dbType == DatabaseType.BIGQUERY) {
            return "jdbc:bigquery://" + host;
        } else if (dbType == DatabaseType.MCP) {
            return host;
        } else {
            return "jdbc:" + dbType.getId() + "://" + host + ":" + port + "/" + database;
        }
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

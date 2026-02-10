package com.datanexus.datanexus.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * Registry of supported database types
 * Works with existing DatabaseConnection entity (type, typeName, typeIcon
 * fields)
 */
@Getter
public enum DatabaseType {
    POSTGRESQL("postgresql", "PostgreSQL", "🐘", "5432"),
    MYSQL("mysql", "MySQL", "🐬", "3306"),
    MONGODB("mongodb", "MongoDB", "🍃", "27017"),
    SQLITE("sqlite", "SQLite", "📦", null),
    SUPABASE("supabase", "Supabase", "⚡", "5432"),
    STARROCKS("starrocks", "StarRocks", "⭐", "9030"),
    CLICKHOUSE("clickhouse", "ClickHouse", "⚡", "8123"),
    SNOWFLAKE("snowflake", "Snowflake", "❄️", "443"),
    REDIS("redis", "Redis", "🔴", "6379"),
    ELASTICSEARCH("elasticsearch", "Elasticsearch", "🔍", "9200"),
    BIGQUERY("bigquery", "BigQuery", "📊", null),
    MCP("mcp", "MCP Server", "🔌", null);

    private final String id;
    private final String displayName;
    private final String icon;
    private final String defaultPort;

    DatabaseType(String id, String displayName, String icon, String defaultPort) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.defaultPort = defaultPort;
    }

    /**
     * Get all database types as a list
     */
    public static List<DatabaseType> getAllTypes() {
        return Arrays.asList(values());
    }

    /**
     * Find database type by ID
     */
    public static DatabaseType fromId(String id) {
        return Arrays.stream(values())
                .filter(type -> type.id.equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }

    /**
     * Check if this is a SQL database
     */
    public boolean isSql() {
        return switch (this) {
            case POSTGRESQL, MYSQL, SQLITE, SUPABASE, STARROCKS,
                 CLICKHOUSE, SNOWFLAKE, BIGQUERY -> true;
            default -> false;
        };
    }

    /**
     * Check if this is NoSQL
     */
    public boolean isNoSql() {
        return this == MONGODB || this == REDIS || this == ELASTICSEARCH;
    }
}

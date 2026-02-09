package com.datanexus.datanexus.enums;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for DatabaseType enum
 */
class DatabaseTypeTest {

    @Test
    void testGetAllTypes() {
        List<DatabaseType> types = DatabaseType.getAllTypes();
        assertNotNull(types);
        assertTrue(types.size() >= 12);
    }

    @Test
    void testFromId() {
        DatabaseType postgresql = DatabaseType.fromId("postgresql");
        assertNotNull(postgresql);
        assertEquals("PostgreSQL", postgresql.getDisplayName());
        assertEquals("🐘", postgresql.getIcon());
        assertEquals("5432", postgresql.getDefaultPort());
    }

    @Test
    void testFromIdCaseInsensitive() {
        DatabaseType mysql = DatabaseType.fromId("MYSQL");
        assertNotNull(mysql);
        assertEquals("mysql", mysql.getId());
        assertEquals("MySQL", mysql.getDisplayName());
    }

    @Test
    void testFromIdInvalid() {
        DatabaseType invalid = DatabaseType.fromId("invalid_db");
        assertNull(invalid);
    }

    @Test
    void testIsSql() {
        assertTrue(DatabaseType.POSTGRESQL.isSql());
        assertTrue(DatabaseType.MYSQL.isSql());
        assertTrue(DatabaseType.SQLITE.isSql());
        assertTrue(DatabaseType.CLICKHOUSE.isSql());
        assertFalse(DatabaseType.MONGODB.isSql());
        assertFalse(DatabaseType.REDIS.isSql());
    }

    @Test
    void testIsNoSql() {
        assertTrue(DatabaseType.MONGODB.isNoSql());
        assertTrue(DatabaseType.REDIS.isNoSql());
        assertTrue(DatabaseType.ELASTICSEARCH.isNoSql());
        assertFalse(DatabaseType.POSTGRESQL.isNoSql());
        assertFalse(DatabaseType.MYSQL.isNoSql());
    }

    @Test
    void testAllDatabaseTypesHaveRequiredFields() {
        for (DatabaseType type : DatabaseType.values()) {
            assertNotNull(type.getId(), "ID should not be null for " + type);
            assertNotNull(type.getDisplayName(), "Display name should not be null for " + type);
            assertNotNull(type.getIcon(), "Icon should not be null for " + type);
            // defaultPort can be null for some types like SQLite
        }
    }

    @Test
    void testSpecificDatabaseTypes() {
        // PostgreSQL
        assertEquals("postgresql", DatabaseType.POSTGRESQL.getId());
        assertEquals("🐘", DatabaseType.POSTGRESQL.getIcon());

        // MySQL
        assertEquals("mysql", DatabaseType.MYSQL.getId());
        assertEquals("🐬", DatabaseType.MYSQL.getIcon());

        // MongoDB
        assertEquals("mongodb", DatabaseType.MONGODB.getId());
        assertEquals("🍃", DatabaseType.MONGODB.getIcon());

        // SQLite
        assertEquals("sqlite", DatabaseType.SQLITE.getId());
        assertNull(DatabaseType.SQLITE.getDefaultPort());

        // MCP
        assertEquals("mcp", DatabaseType.MCP.getId());
        assertEquals("🔌", DatabaseType.MCP.getIcon());
    }
}

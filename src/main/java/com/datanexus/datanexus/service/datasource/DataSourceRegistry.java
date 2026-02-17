package com.datanexus.datanexus.service.datasource;

import com.datanexus.datanexus.entity.DatabaseConnection;
import com.datanexus.datanexus.enums.DatabaseType;
import com.datanexus.datanexus.repository.DatabaseConnectionRepository;
import com.datanexus.datanexus.service.datasource.impl.DatabaseDataSource;
import com.datanexus.datanexus.service.datasource.impl.MCPServerDataSource;
import com.datanexus.datanexus.service.datasource.impl.MongoDBDataSource;
import com.datanexus.datanexus.service.datasource.impl.ElasticsearchDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing and creating data sources
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataSourceRegistry {

    private final DatabaseConnectionRepository connectionRepository;
    private final DatabaseDataSource.Factory databaseDataSourceFactory;
    private final MCPServerDataSource.Factory mcpDataSourceFactory;
    private final MongoDBDataSource.Factory mongoDataSourceFactory;
    private final ElasticsearchDataSource.Factory elasticsearchDataSourceFactory;

    // Cache of active data sources
    private final Map<String, DataSource> dataSourceCache = new ConcurrentHashMap<>();

    /**
     * Get or create a data source for the given connection
     */
    public DataSource getDataSource(DatabaseConnection connection) {
        String cacheKey = connection.getId().toString();

        return dataSourceCache.computeIfAbsent(cacheKey, key -> {
            log.info("Creating new data source for connection: {} (type: {})",
                    connection.getName(), connection.getType());

            // Use DatabaseType enum to determine the data source type
            DatabaseType dbType = DatabaseType.fromId(connection.getType());

            if (dbType == null) {
                throw new IllegalArgumentException(
                        "Unknown database type: " + connection.getType() +
                                ". Supported types: " + DatabaseType.getAllTypes().stream()
                                        .map(DatabaseType::getId)
                                        .toList());
            }

            // Route based on database type
            // Route based on database type
            if (dbType == DatabaseType.MCP) {
                return mcpDataSourceFactory.create(connection);
            } else if (isSqlDatabase(dbType)) {
                return databaseDataSourceFactory.create(connection);
            } else if (dbType == DatabaseType.MONGODB) {
                return mongoDataSourceFactory.create(connection);
            } else if (dbType == DatabaseType.ELASTICSEARCH) {
                return elasticsearchDataSourceFactory.create(connection);
            } else {
                // Redis, BigQuery, and others
                throw new UnsupportedOperationException(
                        "Database type '" + dbType.getDisplayName() + "' is registered but not yet implemented. " +
                                "Coming soon in future updates!");
            }
        });
    }

    private boolean isSqlDatabase(DatabaseType type) {
        return type == DatabaseType.POSTGRESQL ||
                type == DatabaseType.MYSQL ||
                type == DatabaseType.SQLITE ||
                type == DatabaseType.SUPABASE ||
                type == DatabaseType.STARROCKS ||
                type == DatabaseType.CLICKHOUSE ||
                type == DatabaseType.SNOWFLAKE;
    }

    /**
     * Get data source by connection ID and user ID (with security check)
     */
    public DataSource getDataSourceByConnectionId(Long connectionId, Long userId) {
        DatabaseConnection connection = connectionRepository.findByIdAndUserId(connectionId, userId);
        if (connection == null) {
            log.warn("Connection {} not found for user {}", connectionId, userId);
            return null;
        }
        return getDataSource(connection);
    }

    /**
     * Get all available data sources for a user
     */
    public List<DataSource> getAllDataSources(Long userId) {
        List<DatabaseConnection> connections = connectionRepository.findByUserIdOrderByLastUsedDesc(userId);
        List<DataSource> dataSources = new ArrayList<>();

        for (DatabaseConnection conn : connections) {
            try {
                DataSource ds = getDataSource(conn);
                if (ds.isAvailable()) {
                    dataSources.add(ds);
                }
            } catch (Exception e) {
                log.error("Failed to create data source for connection {}: {}",
                        conn.getId(), e.getMessage());
            }
        }

        return dataSources;
    }

    /**
     * Clear cached data source (useful when connection details change)
     */
    public void clearCache(Long connectionId) {
        dataSourceCache.remove(connectionId.toString());
        log.info("Cleared data source cache for connection: {}", connectionId);
    }

    /**
     * Clear all cached data sources
     */
    public void clearAllCache() {
        int size = dataSourceCache.size();
        dataSourceCache.clear();
        log.info("Cleared all data source cache ({} entries)", size);
    }
}

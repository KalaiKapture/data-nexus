package com.datanexus.datanexus.service;

import com.datanexus.datanexus.dto.connection.*;
import com.datanexus.datanexus.entity.DatabaseConnection;
import com.datanexus.datanexus.entity.User;
import com.datanexus.datanexus.exception.ApiException;
import com.datanexus.datanexus.repository.DatabaseConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConnectionService {

    private final DatabaseConnectionRepository connectionRepository;

    public List<ConnectionDto> getUserConnections(User user) {
        List<DatabaseConnection> connections = connectionRepository.findByUserIdOrderByLastUsedDesc(user.getId());
        return connections.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public TestConnectionResponse testConnection(TestConnectionRequest request) {
        long start = System.currentTimeMillis();
        String jdbcUrl = buildJdbcUrl(request.getType(), request.getHost(), request.getPort(), request.getDatabase());

        try (Connection conn = DriverManager.getConnection(jdbcUrl, request.getUsername(), request.getPassword())) {
            long latency = System.currentTimeMillis() - start;
            String version = conn.getMetaData().getDatabaseProductName() + " " + conn.getMetaData().getDatabaseProductVersion();
            return TestConnectionResponse.builder()
                    .connected(true)
                    .message("Successfully connected to " + request.getType() + " database at " + request.getHost() + ":" + request.getPort())
                    .serverVersion(version)
                    .latency(latency)
                    .build();
        } catch (Exception e) {
            throw ApiException.badRequest("CONNECTION_FAILED",
                    "Unable to connect to database. Please check your credentials.");
        }
    }

    public ConnectionDto createConnection(ConnectionRequest request, User user) {
        DatabaseConnection connection = DatabaseConnection.builder()
                .name(request.getName())
                .type(request.getType())
                .typeName(request.getTypeName())
                .typeIcon(request.getTypeIcon())
                .host(request.getHost())
                .port(request.getPort())
                .database(request.getDatabase())
                .username(request.getUsername())
                .password(request.getPassword())
                .status("connected")
                .user(user)
                .lastUsed(Instant.now())
                .build();

        connection = connectionRepository.save(connection);
        return toDto(connection);
    }

    public ConnectionDto updateConnection(String connectionId, ConnectionRequest request, User user) {
        DatabaseConnection connection = getConnectionEntity(connectionId, user);

        if (request.getName() != null) connection.setName(request.getName());
        if (request.getHost() != null) connection.setHost(request.getHost());
        if (request.getPort() != null) connection.setPort(request.getPort());
        if (request.getDatabase() != null) connection.setDatabase(request.getDatabase());
        if (request.getUsername() != null) connection.setUsername(request.getUsername());
        if (request.getPassword() != null) connection.setPassword(request.getPassword());
        connection.setLastUsed(Instant.now());

        connection = connectionRepository.save(connection);
        return toDto(connection);
    }

    public void deleteConnection(String connectionId, User user) {
        DatabaseConnection connection = getConnectionEntity(connectionId, user);
        connectionRepository.delete(connection);
    }

    public ConnectionDto updateLastUsed(String connectionId, User user) {
        DatabaseConnection connection = getConnectionEntity(connectionId, user);
        connection.setLastUsed(Instant.now());
        connection = connectionRepository.save(connection);
        return ConnectionDto.builder()
                .id(connection.getId())
                .lastUsed(connection.getLastUsed())
                .build();
    }

    public DatabaseConnection getConnectionEntity(String connectionId, User user) {
        DatabaseConnection connection = connectionRepository.findByIdAndUserId(connectionId, user.getId());
        if (connection == null) {
            throw ApiException.notFound("CONNECTION_NOT_FOUND", "Database connection not found");
        }
        return connection;
    }

    public Map<String, Object> getSchema(String connectionId, User user) {
        DatabaseConnection conn = getConnectionEntity(connectionId, user);
        String jdbcUrl = buildJdbcUrl(conn.getType(), conn.getHost(), conn.getPort(), conn.getDatabase());

        try (Connection dbConn = DriverManager.getConnection(jdbcUrl, conn.getUsername(), conn.getPassword())) {
            var metaData = dbConn.getMetaData();
            var rs = metaData.getTables(null, null, "%", new String[]{"TABLE"});
            var tables = new java.util.ArrayList<Map<String, Object>>();

            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                var colRs = metaData.getColumns(null, null, tableName, "%");
                var columns = new java.util.ArrayList<Map<String, Object>>();

                while (colRs.next()) {
                    columns.add(Map.of(
                            "name", colRs.getString("COLUMN_NAME"),
                            "type", colRs.getString("TYPE_NAME"),
                            "nullable", "YES".equals(colRs.getString("IS_NULLABLE"))
                    ));
                }
                colRs.close();

                tables.add(Map.of(
                        "name", tableName,
                        "columns", columns
                ));
            }
            rs.close();

            return Map.of("tables", tables);
        } catch (Exception e) {
            throw ApiException.badRequest("CONNECTION_FAILED", "Failed to retrieve schema: " + e.getMessage());
        }
    }

    private String buildJdbcUrl(String type, String host, String port, String database) {
        return switch (type.toLowerCase()) {
            case "postgresql" -> "jdbc:postgresql://" + host + ":" + port + "/" + database;
            case "mysql" -> "jdbc:mysql://" + host + ":" + port + "/" + database;
            case "mongodb" -> "jdbc:mongodb://" + host + ":" + port + "/" + database;
            default -> "jdbc:" + type + "://" + host + ":" + port + "/" + database;
        };
    }

    private ConnectionDto toDto(DatabaseConnection connection) {
        return ConnectionDto.builder()
                .id(connection.getId())
                .name(connection.getName())
                .type(connection.getType())
                .typeName(connection.getTypeName())
                .typeIcon(connection.getTypeIcon())
                .host(connection.getHost())
                .port(connection.getPort())
                .database(connection.getDatabase())
                .username(connection.getUsername())
                .status(connection.getStatus())
                .createdAt(connection.getCreatedAt())
                .updatedAt(connection.getUpdatedAt())
                .lastUsed(connection.getLastUsed())
                .build();
    }
}

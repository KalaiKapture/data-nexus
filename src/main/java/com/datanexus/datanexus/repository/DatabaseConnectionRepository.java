package com.datanexus.datanexus.repository;

import com.datanexus.datanexus.entity.DatabaseConnection;
import com.datanexus.datanexus.utils.PSQLUtil;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class DatabaseConnectionRepository {

    public List<DatabaseConnection> findByUserIdOrderByLastUsedDesc(String userId) {
        return PSQLUtil.runQuery(
                "FROM DatabaseConnection dc WHERE dc.user.id = :userId ORDER BY dc.lastUsed DESC",
                Map.of("userId", userId),
                DatabaseConnection.class);
    }

    public DatabaseConnection findByIdAndUserId(String id, String userId) {
        return PSQLUtil.getSingleResult(
                "FROM DatabaseConnection dc WHERE dc.id = :id AND dc.user.id = :userId",
                Map.of("id", id, "userId", userId),
                DatabaseConnection.class);
    }

    public DatabaseConnection save(DatabaseConnection connection) {
        return PSQLUtil.saveOrUpdateWithReturn(connection);
    }

    public void delete(DatabaseConnection connection) {
        PSQLUtil.delete(connection);
    }
}

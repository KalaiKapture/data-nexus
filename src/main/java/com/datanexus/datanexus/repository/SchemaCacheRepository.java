package com.datanexus.datanexus.repository;

import com.datanexus.datanexus.entity.SchemaCache;
import com.datanexus.datanexus.utils.PSQLUtil;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

@Repository
public class SchemaCacheRepository {

    public Optional<SchemaCache> findByConnectionIdAndUserId(Long connectionId, Long userId) {
        SchemaCache result = PSQLUtil.getSingleResult(
                "FROM SchemaCache sc WHERE sc.connectionId = :connectionId AND sc.userId = :userId",
                Map.of("connectionId", connectionId, "userId", userId),
                SchemaCache.class);
        return Optional.ofNullable(result);
    }

    public SchemaCache save(SchemaCache cache) {
        return PSQLUtil.saveOrUpdateWithReturn(cache);
    }

    public void deleteByConnectionIdAndUserId(Long connectionId, Long userId) {
        PSQLUtil.runQueryForUpdate(
                "DELETE FROM SchemaCache sc WHERE sc.connectionId = :connectionId AND sc.userId = :userId",
                Map.of("connectionId", connectionId, "userId", userId));
    }

    public void deleteByConnectionId(Long connectionId) {
        PSQLUtil.runQueryForUpdate(
                "DELETE FROM SchemaCache sc WHERE sc.connectionId = :connectionId",
                Map.of("connectionId", connectionId));
    }
}

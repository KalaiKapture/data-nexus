package com.datanexus.datanexus.repository;

import com.datanexus.datanexus.entity.Module;
import com.datanexus.datanexus.utils.PSQLUtil;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class ModuleRepository {

    public List<Module> findByUserIdOrderByCreatedAtDesc(String userId) {
        return PSQLUtil.runQuery(
                "FROM Module m WHERE m.user.id = :userId ORDER BY m.createdAt DESC",
                Map.of("userId", userId),
                Module.class);
    }

    public Module findByIdAndUserId(String id, String userId) {
        return PSQLUtil.getSingleResult(
                "FROM Module m WHERE m.id = :id AND m.user.id = :userId",
                Map.of("id", id, "userId", userId),
                Module.class);
    }

    public Module findByShareId(String shareId) {
        return PSQLUtil.getSingleResult(
                "FROM Module m WHERE m.shareId = :shareId",
                Map.of("shareId", shareId),
                Module.class);
    }

    public Module save(Module module) {
        return PSQLUtil.saveOrUpdateWithReturn(module);
    }

    public void delete(Module module) {
        PSQLUtil.delete(module);
    }
}

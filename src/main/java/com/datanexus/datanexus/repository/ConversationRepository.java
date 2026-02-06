package com.datanexus.datanexus.repository;

import com.datanexus.datanexus.entity.Conversation;
import com.datanexus.datanexus.utils.PSQLUtil;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class ConversationRepository {

    public List<Conversation> findByUserIdOrdered(Long userId, String orderDir, int limit, int offset) {
        String hql = "FROM Conversation c WHERE c.user.id = :userId ORDER BY c.updatedAt " + orderDir;
        return PSQLUtil.runQuery(hql, Map.of("userId", userId), Conversation.class, limit, offset);
    }

    public long countByUserId(Long userId) {
        Long result = PSQLUtil.getSingleResult(
                "SELECT COUNT(c) FROM Conversation c WHERE c.user.id = :userId",
                Map.of("userId", userId),
                Long.class);
        return result != null ? result : 0L;
    }

    public Conversation findByIdAndUserId(Long id, Long userId) {
        return PSQLUtil.getSingleResult(
                "FROM Conversation c WHERE c.id = :id AND c.user.id = :userId",
                Map.of("id", id, "userId", userId),
                Conversation.class);
    }

    public Conversation findByShareId(String shareId) {
        return PSQLUtil.getSingleResult(
                "FROM Conversation c WHERE c.shareId = :shareId",
                Map.of("shareId", shareId),
                Conversation.class);
    }

    public Conversation save(Conversation conversation) {
        return PSQLUtil.saveOrUpdateWithReturn(conversation);
    }

    public void delete(Conversation conversation) {
        PSQLUtil.delete(conversation);
    }
}

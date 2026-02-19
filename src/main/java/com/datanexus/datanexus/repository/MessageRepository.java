package com.datanexus.datanexus.repository;

import com.datanexus.datanexus.entity.Activities;
import com.datanexus.datanexus.entity.Message;
import com.datanexus.datanexus.utils.PSQLUtil;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class MessageRepository {

    public Message save(Message message) {
        return PSQLUtil.saveOrUpdateWithReturn(message);
    }

    public List<Message> findByConversationId(Long conversationId) {
        return PSQLUtil.runQuery(
                "FROM Message m WHERE m.conversation = :conversationId ORDER BY m.createdAt ASC",
                Map.of("conversationId", conversationId),
                Message.class);
    }

    public long countByConversationId(Long conversationId) {
        Long result = PSQLUtil.getSingleResult(
                "SELECT COUNT(m) FROM Message m WHERE m.conversation = :conversationId",
                Map.of("conversationId", conversationId),
                Long.class);
        return result != null ? result : 0L;
    }

    public Activities saveActivity(Activities activity) {
        return PSQLUtil.saveOrUpdateWithReturn(activity);
    }

    public List<Activities> findActivityByConversation(Long conversationId) {
        return PSQLUtil.runQuery(
                "FROM Activities m WHERE m.conversation = :conversationId ORDER BY m.createdAt ASC",
                Map.of("conversationId", conversationId),
                Activities.class);
    }

    public List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId) {
        return findByConversationId(conversationId);
    }

    /**
     * Find a message by its primary key (id).
     * Used by DashboardController to return the full Message to the UI.
     */
    public Message findById(Long id) {
        List<Message> results = PSQLUtil.runQuery(
                "FROM Message m WHERE m.id = :id",
                Map.of("id", id),
                Message.class);
        return results.isEmpty() ? null : results.get(0);
    }
}

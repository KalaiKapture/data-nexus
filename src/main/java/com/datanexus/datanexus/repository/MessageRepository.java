package com.datanexus.datanexus.repository;

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
                "FROM Message m WHERE m.conversation.id = :conversationId ORDER BY m.createdAt ASC",
                Map.of("conversationId", conversationId),
                Message.class);
    }
}

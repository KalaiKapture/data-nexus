package com.datanexus.datanexus.repository;

import com.datanexus.datanexus.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    List<Message> findByConversationIdOrderByTimestampAsc(String conversationId);

    long countByConversationId(String conversationId);
}

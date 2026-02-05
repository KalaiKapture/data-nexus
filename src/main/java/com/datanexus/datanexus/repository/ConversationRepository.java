package com.datanexus.datanexus.repository;

import com.datanexus.datanexus.entity.Conversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {

    List<Conversation> findByUserIdOrderByUpdatedAtDesc(String userId, Pageable pageable);

    long countByUserId(String userId);

    Optional<Conversation> findByIdAndUserId(String id, String userId);

    Optional<Conversation> findByShareId(String shareId);
}

package com.datanexus.datanexus.service.ai;

import com.datanexus.datanexus.entity.Message;
import com.datanexus.datanexus.repository.MessageRepository;
import com.datanexus.datanexus.service.ai.provider.AIResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages conversation state for multi-turn AI interactions
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationStateManager {

    private final MessageRepository messageRepository;

    // In-memory state for active conversations
    private final Map<Long, ConversationState> activeStates = new ConcurrentHashMap<>();

    /**
     * Get or create conversation state
     */
    public ConversationState getOrCreate(Long conversationId) {
        return activeStates.computeIfAbsent(conversationId, id -> {
            ConversationState state = new ConversationState(id);
            // Load conversation history
            state.setConversationHistory(loadHistory(id));
            return state;
        });
    }

    /**
     * Update conversation state
     */
    public void updateState(Long conversationId, AIResponse aiResponse) {
        ConversationState state = getOrCreate(conversationId);
        state.setLastAIResponse(aiResponse);
        state.setLastUpdated(Instant.now());

        // Store in context for potential use
        state.getContext().put("lastIntent", aiResponse.getIntent());
        state.getContext().put("lastResponseType", aiResponse.getType());
    }

    /**
     * Add user message to history
     */
    public void addUserMessage(Long conversationId, String message) {
        ConversationState state = getOrCreate(conversationId);
        // History will be loaded from DB when needed
        state.setLastUpdated(Instant.now());
    }

    /**
     * Clean up old state
     */
    public void cleanup(Long conversationId) {
        activeStates.remove(conversationId);
        log.info("Cleaned up conversation state for conversation {}", conversationId);
    }

    /**
     * Clean up stale states (older than 1 hour)
     */
    public void cleanupStale() {
        Instant threshold = Instant.now().minusSeconds(3600);
        activeStates.entrySet().removeIf(entry -> entry.getValue().getLastUpdated().isBefore(threshold));
    }

    /**
     * Load conversation history from database
     */
    private List<Message> loadHistory(Long conversationId) {
        try {
            return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        } catch (Exception e) {
            log.warn("Failed to load conversation history for {}: {}", conversationId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Conversation state container
     */
    @Getter
    @Setter
    public static class ConversationState {
        private final Long conversationId;
        private AIResponse lastAIResponse;
        private List<Message> conversationHistory;
        private Map<String, Object> context;
        private Instant lastUpdated;

        public ConversationState(Long conversationId) {
            this.conversationId = conversationId;
            this.conversationHistory = new ArrayList<>();
            this.context = new HashMap<>();
            this.lastUpdated = Instant.now();
        }
    }
}

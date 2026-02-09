package com.datanexus.datanexus.service.ai;

import com.datanexus.datanexus.entity.Message;
import com.datanexus.datanexus.repository.MessageRepository;
import com.datanexus.datanexus.service.ai.provider.AIResponse;
import com.datanexus.datanexus.service.ai.provider.AIResponseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConversationStateManager
 */
@ExtendWith(MockitoExtension.class)
class ConversationStateManagerTest {

    @Mock
    private MessageRepository messageRepository;

    private ConversationStateManager stateManager;

    @BeforeEach
    void setUp() {
        stateManager = new ConversationStateManager(messageRepository);
    }

    @Test
    void testGetOrCreate_NewConversation_CreatesNewState() {
        // Arrange
        Long conversationId = 1L;
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(new ArrayList<>());

        // Act
        ConversationStateManager.ConversationState state = stateManager.getOrCreate(conversationId);

        // Assert
        assertNotNull(state);
        assertEquals(conversationId, state.getConversationId());
        assertNotNull(state.getConversationHistory());
        assertNotNull(state.getContext());
        assertNotNull(state.getLastUpdated());
    }

    @Test
    void testGetOrCreate_ExistingConversation_ReturnsCachedState() {
        // Arrange
        Long conversationId = 1L;
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(new ArrayList<>());

        // Act
        ConversationStateManager.ConversationState state1 = stateManager.getOrCreate(conversationId);
        ConversationStateManager.ConversationState state2 = stateManager.getOrCreate(conversationId);

        // Assert
        assertSame(state1, state2);
        verify(messageRepository, times(1)).findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    @Test
    void testUpdateState_UpdatesStateFields() {
        // Arrange
        Long conversationId = 1L;
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(new ArrayList<>());

        AIResponse aiResponse = AIResponse.builder()
                .type(AIResponseType.READY_TO_EXECUTE)
                .content("Test content")
                .intent("Test intent")
                .build();

        ConversationStateManager.ConversationState state = stateManager.getOrCreate(conversationId);
        Instant beforeUpdate = state.getLastUpdated();

        // Act
        stateManager.updateState(conversationId, aiResponse);

        // Assert
        assertEquals(aiResponse, state.getLastAIResponse());
        assertTrue(state.getLastUpdated().isAfter(beforeUpdate));
        assertEquals("Test intent", state.getContext().get("lastIntent"));
        assertEquals(AIResponseType.READY_TO_EXECUTE, state.getContext().get("lastResponseType"));
    }

    @Test
    void testAddUserMessage_UpdatesTimestamp() {
        // Arrange
        Long conversationId = 1L;
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(new ArrayList<>());

        ConversationStateManager.ConversationState state = stateManager.getOrCreate(conversationId);
        Instant beforeAdd = state.getLastUpdated();

        // Act
        stateManager.addUserMessage(conversationId, "Test message");

        // Assert
        assertTrue(state.getLastUpdated().isAfter(beforeAdd));
    }

    @Test
    void testCleanup_RemovesState() {
        // Arrange
        Long conversationId = 1L;
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(new ArrayList<>());

        stateManager.getOrCreate(conversationId);

        // Act
        stateManager.cleanup(conversationId);

        // Assert
        // Getting again should create new state (not cached)
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(new ArrayList<>());
        stateManager.getOrCreate(conversationId);
        verify(messageRepository, times(2)).findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    @Test
    void testLoadHistory_LoadsMessagesFromRepository() {
        // Arrange
        Long conversationId = 1L;
        List<Message> mockMessages = new ArrayList<>();
        Message msg = new Message();
        msg.setId(1L);
        msg.setContent("Test message");
        mockMessages.add(msg);

        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(mockMessages);

        // Act
        ConversationStateManager.ConversationState state = stateManager.getOrCreate(conversationId);

        // Assert
        assertNotNull(state.getConversationHistory());
        assertEquals(1, state.getConversationHistory().size());
        assertEquals("Test message", state.getConversationHistory().get(0).getContent());
    }
}

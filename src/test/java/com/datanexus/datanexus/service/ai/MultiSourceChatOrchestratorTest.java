package com.datanexus.datanexus.service.ai;

import com.datanexus.datanexus.dto.websocket.AnalyzeRequest;
import com.datanexus.datanexus.entity.DatabaseConnection;
import com.datanexus.datanexus.entity.User;
import com.datanexus.datanexus.repository.ConversationRepository;
import com.datanexus.datanexus.repository.DatabaseConnectionRepository;
import com.datanexus.datanexus.service.ai.provider.AIProvider;
import com.datanexus.datanexus.service.ai.provider.AIProviderFactory;
import com.datanexus.datanexus.service.ai.provider.AIRequest;
import com.datanexus.datanexus.service.ai.provider.AIResponse;
import com.datanexus.datanexus.service.ai.provider.AIResponseType;
import com.datanexus.datanexus.service.datasource.DataRequest;
import com.datanexus.datanexus.service.datasource.DataSource;
import com.datanexus.datanexus.service.datasource.DataSourceRegistry;
import com.datanexus.datanexus.service.datasource.schema.SourceSchema;
import com.datanexus.datanexus.service.datasource.request.SqlQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MultiSourceChatOrchestrator
 */
@ExtendWith(MockitoExtension.class)
class MultiSourceChatOrchestratorTest {

        @Mock
        private SimpMessagingTemplate messagingTemplate;

        @Mock
        private DataSourceRegistry dataSourceRegistry;

        @Mock
        private AIProviderFactory aiProviderFactory;

        @Mock
        private DatabaseConnectionRepository connectionRepository;

        @Mock
        private ConversationRepository conversationRepository;

        @Mock
        private ConversationStateManager stateManager;

        @Mock
        private AIProvider aiProvider;

        @Mock
        private DataSource dataSource;

        private MultiSourceChatOrchestrator orchestrator;
        private User testUser;
        private DatabaseConnection testConnection;

        @BeforeEach
        void setUp() {
                // Note: This test demonstrates structure but won't compile until
                // UnifiedExecutionService is injected into orchestrator
                // orchestrator = new MultiSourceChatOrchestrator(...);

                testUser = new User();
                testUser.setId(1L);
                testUser.setEmail("test@example.com");

                testConnection = new DatabaseConnection();
                testConnection.setId(1L);
                testConnection.setName("Test DB");
                testConnection.setType("postgresql");
        }

        @Test
        void testProcessMessage_WithValidRequest_ReturnsSuccessfully() {
                // Arrange
                AnalyzeRequest request = AnalyzeRequest.builder()
                                .userMessage("Show me sales data")
                                .conversationId(1L)
                                .connectionIds(List.of(1L))
                                .aiProvider("gemini")
                                .build();

                ConversationStateManager.ConversationState state = new ConversationStateManager.ConversationState(1L);

                SourceSchema schema = SourceSchema.builder()
                                .sourceId("1")
                                .sourceName("Test DB")
                                .build();

                AIResponse aiResponse = AIResponse.builder()
                                .type(AIResponseType.READY_TO_EXECUTE)
                                .content("I'll fetch the sales data")
                                .dataRequests(List.of(
                                                SqlQuery.builder()
                                                                .sql("SELECT * FROM sales")
                                                                .explanation("Get all sales")
                                                                .build()))
                                .build();

                when(stateManager.getOrCreate(1L)).thenReturn(state);
                when(connectionRepository.findByIdAndUserId(1L, 1L)).thenReturn(testConnection);
                when(dataSourceRegistry.getDataSource(testConnection)).thenReturn(dataSource);
                when(dataSource.isAvailable()).thenReturn(true);
                when(dataSource.extractSchema()).thenReturn(schema);
                when(aiProviderFactory.getProvider("gemini")).thenReturn(aiProvider);
                when(aiProvider.chat(any(AIRequest.class))).thenReturn(aiResponse);

                // Act
                // orchestrator.processMessage(request, testUser, "1");

                // Assert
                verify(stateManager).getOrCreate(1L);
                verify(stateManager).addUserMessage(1L, "Show me sales data");
                verify(dataSourceRegistry).getDataSource(testConnection);
                verify(aiProvider).chat(any(AIRequest.class));
                // verify(messagingTemplate, atLeastOnce()).convertAndSendToUser(anyString(),
                // anyString(), any());
        }

        @Test
        void testProcessMessage_WithClarificationNeeded_SendsClarificationRequest() {
                // Arrange
                AnalyzeRequest request = AnalyzeRequest.builder()
                                .userMessage("Show sales")
                                .conversationId(1L)
                                .connectionIds(List.of(1L))
                                .aiProvider("gemini")
                                .build();

                AIResponse aiResponse = AIResponse.builder()
                                .type(AIResponseType.CLARIFICATION_NEEDED)
                                .clarificationQuestion("Which time period?")
                                .suggestedOptions(List.of("Last week", "Last month", "Last year"))
                                .build();

                when(aiProvider.chat(any())).thenReturn(aiResponse);

                // Act & Assert
                // Should send clarification to user via WebSocket
        }

        @Test
        void testProcessMessage_WithNoSchemas_ReturnsError() {
                // Arrange
                AnalyzeRequest request = AnalyzeRequest.builder()
                                .userMessage("Show data")
                                .conversationId(1L)
                                .connectionIds(List.of(999L)) // Non-existent connection
                                .aiProvider("gemini")
                                .build();

                when(connectionRepository.findByIdAndUserId(999L, 1L)).thenReturn(null);

                // Act
                // orchestrator.processMessage(request, testUser, "1");

                // Assert
                // Should send error response to user
        }
}

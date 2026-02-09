package com.datanexus.datanexus.service.ai;

import com.datanexus.datanexus.dto.websocket.AnalyzeRequest;
import com.datanexus.datanexus.dto.websocket.AnalyzeResponse;
import com.datanexus.datanexus.dto.websocket.ClarificationRequest;
import com.datanexus.datanexus.entity.Conversation;
import com.datanexus.datanexus.entity.DatabaseConnection;
import com.datanexus.datanexus.entity.Message;
import com.datanexus.datanexus.entity.User;
import com.datanexus.datanexus.repository.ConversationRepository;
import com.datanexus.datanexus.repository.DatabaseConnectionRepository;
import com.datanexus.datanexus.repository.MessageRepository;
import com.datanexus.datanexus.service.ai.provider.*;
import com.datanexus.datanexus.service.datasource.DataRequest;
import com.datanexus.datanexus.service.datasource.DataSource;
import com.datanexus.datanexus.service.datasource.DataSourceRegistry;
import com.datanexus.datanexus.service.datasource.UnifiedExecutionService;
import com.datanexus.datanexus.service.datasource.schema.SourceSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * New multi-source chat orchestrator with AI provider support
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MultiSourceChatOrchestrator {

    private final SimpMessagingTemplate messagingTemplate;
    private final DataSourceRegistry dataSourceRegistry;
    private final AIProviderFactory aiProviderFactory;
    private final DatabaseConnectionRepository connectionRepository;
    private final ConversationRepository conversationRepository;
    private final UnifiedExecutionService executionService;
    private final ConversationStateManager stateManager;
    private final MessageRepository messageRepository;

    /**
     * Process user message
     */
    public void processMessage(AnalyzeRequest request, User user, String wsUser) {
        Long conversationId = resolveConversation(request, user);

        try {
            // Get or create conversation state
            ConversationStateManager.ConversationState state = stateManager.getOrCreate(conversationId);

            // Add user message to state
            stateManager.addUserMessage(conversationId, request.getUserMessage());

            // Reset any previous AI response
            Message systemMessage = addSystemMessage(conversationId, "Processing your request...");

            // Phase 1: Extract schemas from all data sources
            sendActivity(conversationId, wsUser, AIActivityPhase.MAPPING_DATA_SOURCES, "in_progress",
                    "Extracting schemas from your data sources...");

            List<SourceSchema> schemas = extractAllSchemas(request, user);

            if (schemas.isEmpty()) {
                sendErrorResponse(conversationId, wsUser, "NO_SCHEMAS",
                        "Could not extract schemas from any of the specified connections.",
                        "Please verify your database connections are active and accessible.");
                return;
            }

            // Phase 2: Get AI provider
            sendActivity(conversationId, wsUser, AIActivityPhase.UNDERSTANDING_INTENT, "in_progress",
                    "Analyzing your question with AI...");

            String providerName = request.getAiProvider() != null ? request.getAiProvider() : "gemini";
            AIProvider aiProvider = aiProviderFactory.getProvider(providerName);

            // Phase 3: Build AI request with conversation history
            AIRequest aiRequest = AIRequest.builder()
                    .userMessage(request.getUserMessage())
                    .availableSchemas(schemas)
                    .conversationHistory(state.getConversationHistory())
                    .preferences(new HashMap<>())
                    .build();

            // Phase 4: Get AI response
            AIResponse aiResponse = aiProvider.chat(aiRequest);

            // Phase 5: Handle response type
            switch (aiResponse.getType()) {
                case CLARIFICATION_NEEDED -> {
                    sendClarificationRequest(conversationId, wsUser, aiResponse);
                    return;
                }

                case DIRECT_ANSWER -> {
                    sendDirectAnswer(conversationId, wsUser, aiResponse);
                    return;
                }

                case READY_TO_EXECUTE -> {
                    // Continue to execution
                }
            }

            // Update state with AI response
            stateManager.updateState(conversationId, aiResponse);

            // Phase 6: Execute data requests
            sendActivity(conversationId, wsUser, AIActivityPhase.EXECUTING_QUERIES, "in_progress",
                    "Executing queries across your data sources...");

            List<AnalyzeResponse.QueryResult> queryResults = executionService.executeAll(
                    aiResponse.getDataRequests(),
                    request.getConnectionIds(),
                    user); // Phase 7: Send final response
            sendActivity(conversationId, wsUser, AIActivityPhase.COMPLETED, "success",
                    "Analysis complete!");

            AnalyzeResponse response = AnalyzeResponse.success(
                    conversationId,
                    aiResponse.getContent(),
                    queryResults,
                    null // TODO: Visualization suggestion
            );

            messagingTemplate.convertAndSendToUser(wsUser, "/queue/ai/response", response);

        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage(), e);
            sendErrorResponse(conversationId, wsUser, "INTERNAL_ERROR",
                    "An unexpected error occurred while processing your request: " + e.getMessage(),
                    "Please try again or contact support if the problem persists.");
        }
    }

    private Long resolveConversation(AnalyzeRequest request, User user) {
        if (request.getConversationId() != null) {
            return request.getConversationId();
        }

        // Create new conversation
        Conversation conversation = new Conversation();
        conversation.setName("Chat " + Instant.now().toString());
        conversation.setUser(user.getId());
        conversation.setConnectionIds(request.getConnectionIds() != null
                ? String.join(",", request.getConnectionIds().stream().map(String::valueOf).toList())
                : "");

        conversation = conversationRepository.save(conversation);
        return conversation.getId();
    }

    private List<SourceSchema> extractAllSchemas(AnalyzeRequest request, User user) {
        List<SourceSchema> schemas = new ArrayList<>();

        if (request.getConnectionIds() != null) {
            for (Long connId : request.getConnectionIds()) {
                try {
                    DatabaseConnection conn = connectionRepository.findByIdAndUserId(connId, user.getId());
                    if (conn == null) {
                        log.warn("Connection {} not found for user {}", connId, user.getId());
                        continue;
                    }

                    DataSource dataSource = dataSourceRegistry.getDataSource(conn);
                    if (dataSource != null && dataSource.isAvailable()) {
                        schemas.add(dataSource.extractSchema());
                    }
                } catch (Exception e) {
                    log.error("Failed to extract schema for connection {}: {}", connId, e.getMessage());
                }
            }
        }

        return schemas;
    }

    private void sendClarificationRequest(Long conversationId, String wsUser, AIResponse aiResponse) {
        sendActivity(conversationId, wsUser, AIActivityPhase.UNDERSTANDING_INTENT, "waiting",
                "I need clarification...");

        ClarificationRequest clarificationRequest = ClarificationRequest.builder()
                .conversationId(conversationId)
                .question(aiResponse.getClarificationQuestion())
                .suggestedOptions(aiResponse.getSuggestedOptions())
                .timestamp(Instant.now())
                .build();

        messagingTemplate.convertAndSendToUser(wsUser, "/queue/ai/clarification", clarificationRequest);
    }

    private void sendDirectAnswer(Long conversationId, String wsUser, AIResponse aiResponse) {
        sendActivity(conversationId, wsUser, AIActivityPhase.COMPLETED, "success",
                "Answer ready!");

        AnalyzeResponse response = AnalyzeResponse.success(
                conversationId,
                aiResponse.getContent(),
                new ArrayList<>(),
                null);

        messagingTemplate.convertAndSendToUser(wsUser, "/queue/ai/response", response);
    }

    private void sendActivity(Long conversationId, String wsUser, AIActivityPhase phase,
                              String status, String message) {
        messagingTemplate.convertAndSendToUser(wsUser, "/queue/ai/activity",
                Map.of(
                        "conversationId", conversationId,
                        "phase", phase.getCode(),
                        "status", status,
                        "message", message,
                        "timestamp", Instant.now()));
    }

    private void sendErrorResponse(Long conversationId, String wsUser, String code,
                                   String message, String suggestion) {
        sendActivity(conversationId, wsUser, AIActivityPhase.ERROR, "error", message);

        AnalyzeResponse errorResponse = AnalyzeResponse.error(conversationId, code, message, suggestion);

        messagingTemplate.convertAndSendToUser(wsUser, "/queue/ai/response", errorResponse);
        messagingTemplate.convertAndSendToUser(wsUser, "/queue/ai/error", errorResponse);
    }

    public Message addSystemMessage(Long conversationId, String content) {
        Message message = Message.builder()
                .content(content)
                .sentByUser(false)
                .conversation(conversationId)
                .build();
        return messageRepository.save(message);
    }
}

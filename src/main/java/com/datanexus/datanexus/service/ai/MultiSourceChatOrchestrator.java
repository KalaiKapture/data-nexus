package com.datanexus.datanexus.service.ai;

import com.datanexus.datanexus.dto.websocket.AIActivityMessage;
import com.datanexus.datanexus.dto.websocket.AnalyzeRequest;
import com.datanexus.datanexus.dto.websocket.AnalyzeResponse;
import com.datanexus.datanexus.dto.websocket.ClarificationRequest;
import com.datanexus.datanexus.entity.*;
import com.datanexus.datanexus.repository.ConversationRepository;
import com.datanexus.datanexus.repository.DatabaseConnectionRepository;
import com.datanexus.datanexus.repository.MessageRepository;
import com.datanexus.datanexus.service.SchemaCacheService;
import com.datanexus.datanexus.service.ai.provider.*;
import com.datanexus.datanexus.service.datasource.DataSource;
import com.datanexus.datanexus.service.datasource.DataSourceRegistry;
import com.datanexus.datanexus.service.datasource.UnifiedExecutionService;
import com.datanexus.datanexus.service.datasource.schema.SourceSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONObject;
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
    private final SchemaCacheService schemaCacheService;
    private final DataAnalysisService dataAnalysisService;

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
            Message userMessage = Message.builder()
                    .content(request.getUserMessage())
                    .sentByUser(true)
                    .conversation(conversationId)
                    .build();
            messageRepository.save(userMessage);
            sendMessage(conversationId, wsUser, AIActivityPhase.UNDERSTANDING_INTENT, "received",
                    JSONObject.fromObject(userMessage).toString());

            // Reset any previous AI response
            Message systemMessage = addSystemMessage(conversationId, "Processing your request...", wsUser, true,
                    "SYSTEM_MESSAGE");

            // Phase 1: Extract schemas from all data sources
            sendActivity(conversationId, wsUser, AIActivityPhase.MAPPING_DATA_SOURCES, "in_progress",
                    "Extracting schemas from your data sources...", systemMessage);

            List<SourceSchema> schemas = getCachedSchemas(request, user);

            if (schemas.isEmpty()) {
                sendErrorResponse(conversationId, wsUser, "NO_SCHEMAS",
                        "Could not extract schemas from any of the specified connections.",
                        "Please verify your database connections are active and accessible.");
                return;
            }

            // Phase 2: Get AI provider
            sendActivity(conversationId, wsUser, AIActivityPhase.AI_THINKING, "in_progress",
                    "AI is analyzing your question...", systemMessage);

            String providerName = request.getAiProvider() != null ? request.getAiProvider() : "gemini";
            AIProvider aiProvider = aiProviderFactory.getProvider(providerName);

            // Phase 3: Build AI request with conversation history
            AIRequest aiRequest = AIRequest.builder()
                    .userMessage(request.getUserMessage())
                    .availableSchemas(schemas)
                    .conversationHistory(state.getConversationHistory())
                    .preferences(new HashMap<>())
                    .userId(String.valueOf(user.getId()))
                    .conversationId(String.valueOf(conversationId))
                    .firstMessage(state.getConversationHistory().isEmpty())
                    .build();

            // Phase 4: Stream AI response — each chunk is forwarded as an activity
            AIResponse aiResponse = aiProvider.streamChat(aiRequest, chunk -> {
                sendActivity(conversationId, wsUser, AIActivityPhase.AI_THINKING,
                        "in_progress", chunk, systemMessage);
            });

            // Phase 5: Handle response type
            switch (aiResponse.getType()) {
                case CLARIFICATION_NEEDED -> {
                    sendClarificationRequest(conversationId, wsUser, aiResponse, systemMessage);
                    return;
                }

                case DIRECT_ANSWER -> {
                    sendDirectAnswer(conversationId, wsUser, aiResponse, systemMessage);
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
                    "Executing queries across your data sources...", systemMessage);

            List<AnalyzeResponse.QueryResult> queryResults = executionService.executeAll(
                    aiResponse.getDataRequests(),
                    request.getConnectionIds(),
                    user);

            // ── System Response 1: Query results ─────────────────────────────
            // Send immediately after execution so the user sees data right away.
            sendActivity(conversationId, wsUser, AIActivityPhase.EXECUTING_QUERIES, "success",
                    "Queries executed successfully.", systemMessage);

            AnalyzeResponse queryResponse = AnalyzeResponse.queryResult(
                    conversationId,
                    aiResponse.getContent(),
                    queryResults);

            sendSystemResponse(conversationId, wsUser, queryResponse, "SYSTEM_RESPONSE");

            // Check if any query returned data rows before proceeding
            boolean hasData = queryResults.stream()
                    .anyMatch(qr -> qr.getData() != null && !qr.getData().isEmpty());

            if (!hasData) {
                // No data — nothing to analyse or visualise, mark complete
                sendActivity(conversationId, wsUser, AIActivityPhase.COMPLETED, "success",
                        "Queries returned no data rows.", systemMessage);
                return;
            }

            // ── System Response 2: AI analysis ───────────────────────────────
            sendActivity(conversationId, wsUser, AIActivityPhase.ANALYZING_DATA, "in_progress",
                    "AI is analysing your data...", systemMessage);

            DataAnalysisService.AnalysisResult analysisResult = dataAnalysisService.analyzeResults(
                    aiProvider,
                    request.getUserMessage(),
                    queryResults,
                    chunk -> sendActivity(conversationId, wsUser, AIActivityPhase.ANALYZING_DATA,
                            "in_progress", chunk, systemMessage));

            sendActivity(conversationId, wsUser, AIActivityPhase.ANALYZING_DATA, "success",
                    "Analysis complete.", systemMessage);

            AnalyzeResponse analysisResponse = AnalyzeResponse.analysisResponse(
                    conversationId,
                    analysisResult.getAnalysis());

            sendSystemResponse(conversationId, wsUser, analysisResponse, "SYSTEM_RESPONSE_ANALYSIS");

            // ── System Response 3: HTML dashboard ────────────────────────────
            sendActivity(conversationId, wsUser, AIActivityPhase.GENERATING_DASHBOARD, "in_progress",
                    "Generating visual dashboard...", systemMessage);

            DataAnalysisService.DashboardResult dashboardResult = dataAnalysisService.generateDashboard(
                    aiProvider,
                    analysisResult,
                    queryResults,
                    chunk -> sendActivity(conversationId, wsUser, AIActivityPhase.GENERATING_DASHBOARD,
                            "in_progress", chunk, systemMessage));

            sendActivity(conversationId, wsUser, AIActivityPhase.COMPLETED, "success",
                    "Dashboard ready!", systemMessage);

            AnalyzeResponse dashboardResponse = AnalyzeResponse.dashboardResponse(
                    conversationId,
                    dashboardResult.getHtml(),
                    dashboardResult.getDashboardId());

            sendSystemResponse(conversationId, wsUser, dashboardResponse, "SYSTEM_RESPONSE_UI");

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

    /**
     * Get schemas from cache. Falls back to live extraction and auto-caches on
     * miss.
     */
    private List<SourceSchema> getCachedSchemas(AnalyzeRequest request, User user) {
        List<SourceSchema> schemas = new ArrayList<>();

        if (request.getConnectionIds() != null) {
            for (Long connId : request.getConnectionIds()) {
                try {
                    // Try cache first
                    Optional<SourceSchema> cached = schemaCacheService.getCachedSchema(connId, user.getId());
                    if (cached.isPresent()) {
                        schemas.add(cached.get());
                        log.debug("Using cached schema for connection {}", connId);
                        continue;
                    }

                    // Schema is served from the LLM model service — extract live for prompt context
                    log.info("Extracting live schema for connection {}", connId);
                    DatabaseConnection conn = connectionRepository.findByIdAndUserId(connId, user.getId());
                    if (conn == null) {
                        log.warn("Connection {} not found for user {}", connId, user.getId());
                        continue;
                    }

                    DataSource dataSource = dataSourceRegistry.getDataSource(conn);
                    if (dataSource != null && dataSource.isAvailable()) {
                        schemas.add(dataSource.extractSchema());
                        // Train the LLM model service with this connection's schema
                        try {
                            schemaCacheService.cacheSchema(connId, user.getId());
                        } catch (Exception cacheEx) {
                            log.warn("Failed to train schema for connection {}: {}", connId, cacheEx.getMessage());
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to get schema for connection {}: {}", connId, e.getMessage());
                }
            }
        }

        return schemas;
    }

    /**
     * Saves an {@link AnalyzeResponse} as a system message in the DB and then
     * pushes it to the client's personal WebSocket queue so the frontend receives
     * it as a distinct chat bubble / card.
     */
    private void sendSystemResponse(Long conversationId, String wsUser,
                                    AnalyzeResponse response, String messageType) {
        Message saved = addSystemMessage(conversationId,
                JSONObject.fromObject(response).toString(), wsUser, false, messageType);
        // Push directly to the client's personal queue so the frontend receives it
        messagingTemplate.convertAndSendToUser(wsUser, "/queue/ai/response", saved);
    }

    private void sendClarificationRequest(Long conversationId, String wsUser, AIResponse aiResponse,
                                          Message systemMessage) {
        sendActivity(conversationId, wsUser, AIActivityPhase.UNDERSTANDING_INTENT, "waiting",
                "I need clarification...", systemMessage);

        ClarificationRequest clarificationRequest = ClarificationRequest.builder()
                .conversationId(conversationId)
                .question(aiResponse.getContent())
                .suggestedOptions(aiResponse.getSuggestedOptions())
                .intent(aiResponse.getIntent())
                .timestamp(Instant.now())
                .build();
        Message message = addSystemMessage(conversationId, JSONObject.fromObject(clarificationRequest).toString(),
                wsUser, false, "CLARIFICATION_REQUEST");
        AIActivityMessage activity = AIActivityMessage.of(AIActivityPhase.UNDERSTANDING_INTENT.getCode(), "clarification", JSONObject.fromObject(message).toString(), conversationId);
        messagingTemplate.convertAndSendToUser(wsUser, "/queue/ai/message", activity);
    }

    private void sendDirectAnswer(Long conversationId, String wsUser, AIResponse aiResponse, Message systemMessage) {
        sendActivity(conversationId, wsUser, AIActivityPhase.COMPLETED, "success",
                "Answer ready!", systemMessage);

        Message message = addSystemMessage(conversationId, aiResponse.getContent(),
                wsUser, false, "SYSTEM_RESPONSE");

        messagingTemplate.convertAndSendToUser(wsUser, "/queue/ai/response", message);
    }

    private void sendActivity(Long conversationId, String wsUser, AIActivityPhase phase,
                              String status, String message, Message systemMessage) {
        Activities activities = Activities.builder()
                .conversation(conversationId)
                .content(message)
                .type("ACTIVITY")
                .messageId(systemMessage.getId())
                .build();
        activities = messageRepository.saveActivity(activities);
        messagingTemplate.convertAndSendToUser(wsUser, "/queue/ai/activity",
                Map.of(
                        "conversationId", conversationId,
                        "phase", phase.getCode(),
                        "status", status,
                        "message", JSONObject.fromObject(activities).toString(),
                        "timestamp", Instant.now()));
    }

    private void sendErrorResponse(Long conversationId, String wsUser, String code,
                                   String message, String suggestion) {
        AnalyzeResponse errorResponse = AnalyzeResponse.error(conversationId, code, message, suggestion);

        messagingTemplate.convertAndSendToUser(wsUser, "/queue/ai/response", errorResponse);
        messagingTemplate.convertAndSendToUser(wsUser, "/queue/ai/error", errorResponse);
    }

    public Message addSystemMessage(Long conversationId, String content, String wsUser, boolean sendActivity,
                                    String type) {
        Message message = Message.builder()
                .content(content)
                .sentByUser(false)
                .type(type)
                .conversation(conversationId)
                .build();
        message = messageRepository.save(message);
        if (sendActivity) {
            sendMessage(conversationId, wsUser, AIActivityPhase.UNDERSTANDING_INTENT, "Initiated",
                    JSONObject.fromObject(message).toString());
        }
        return message;
    }

    private void sendMessage(Long conversationId, String user, AIActivityPhase phase,
                             String status, String message) {
        AIActivityMessage activity = AIActivityMessage.of(phase.getCode(), status, message, conversationId);

        messagingTemplate.convertAndSendToUser(
                user,
                "/queue/ai/message",
                activity);

    }
}

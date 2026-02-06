package com.datanexus.datanexus.service.ai;

import com.datanexus.datanexus.dto.websocket.AIActivityMessage;
import com.datanexus.datanexus.dto.websocket.AnalyzeRequest;
import com.datanexus.datanexus.dto.websocket.AnalyzeResponse;
import com.datanexus.datanexus.entity.Conversation;
import com.datanexus.datanexus.entity.DatabaseConnection;
import com.datanexus.datanexus.entity.Message;
import com.datanexus.datanexus.entity.User;
import com.datanexus.datanexus.repository.ConversationRepository;
import com.datanexus.datanexus.repository.DatabaseConnectionRepository;
import com.datanexus.datanexus.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIAnalystOrchestrator {

    private final SimpMessagingTemplate messagingTemplate;
    private final DatabaseConnectionRepository connectionRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final SchemaService schemaService;
    private final QueryGeneratorService queryGeneratorService;
    private final QueryExecutionService queryExecutionService;

    public void processAnalyzeRequest(AnalyzeRequest request, User user) {
        Long conversationId = request.getConversationId();

        try {
            // Phase 1: Understanding user intent
            sendActivity(conversationId, user, AIActivityPhase.UNDERSTANDING_INTENT,
                    "in_progress", "Analyzing your request: \"" + truncate(request.getUserMessage(), 100) + "\"");

            // Resolve or create conversation
            conversationId = resolveConversation(request, user);

            // Store user message
            storeUserMessage(conversationId, request.getUserMessage());

            sendActivity(conversationId, user, AIActivityPhase.UNDERSTANDING_INTENT,
                    "completed", "User intent understood");

            // Phase 2: Mapping to data sources
            sendActivity(conversationId, user, AIActivityPhase.MAPPING_DATA_SOURCES,
                    "in_progress", "Identifying data sources from " + request.getConnectionIds().size() + " connection(s)");

            List<DatabaseConnection> connections = resolveConnections(request.getConnectionIds(), user);

            if (connections.isEmpty()) {
                sendErrorResponse(conversationId, user, "NO_CONNECTIONS",
                        "No valid database connections found for the provided connection IDs.",
                        "Please check your connection IDs and ensure they are configured correctly.");
                return;
            }

            sendActivity(conversationId, user, AIActivityPhase.MAPPING_DATA_SOURCES,
                    "completed", "Mapped to " + connections.size() + " data source(s): " +
                    connections.stream().map(DatabaseConnection::getName).collect(Collectors.joining(", ")));

            // Phase 3: Analyzing schemas
            sendActivity(conversationId, user, AIActivityPhase.ANALYZING_SCHEMAS,
                    "in_progress", "Retrieving schema information from connected databases");

            Map<Long, SchemaService.DatabaseSchema> schemas = new LinkedHashMap<>();
            for (DatabaseConnection conn : connections) {
                try {
                    SchemaService.DatabaseSchema schema = schemaService.extractSchema(conn);
                    schemas.put(conn.getId(), schema);
                    sendActivity(conversationId, user, AIActivityPhase.ANALYZING_SCHEMAS,
                            "in_progress", "Schema loaded for '" + conn.getName() + "': " +
                            schema.getTables().size() + " table(s) found");
                } catch (Exception e) {
                    log.error("Failed to extract schema for connection {}: {}", conn.getId(), e.getMessage());
                    sendActivity(conversationId, user, AIActivityPhase.ANALYZING_SCHEMAS,
                            "in_progress", "Warning: Could not load schema for '" + conn.getName() + "'");
                }
            }

            if (schemas.isEmpty()) {
                sendErrorResponse(conversationId, user, "SCHEMA_ERROR",
                        "Could not retrieve schema from any of the connected databases.",
                        "Please verify your database connections are active and accessible.");
                return;
            }

            sendActivity(conversationId, user, AIActivityPhase.ANALYZING_SCHEMAS,
                    "completed", "Schema analysis complete for " + schemas.size() + " database(s)");

            // Phase 4: Generating queries
            sendActivity(conversationId, user, AIActivityPhase.GENERATING_QUERIES,
                    "in_progress", "Generating safe read-only queries based on your request");

            QueryGeneratorService.QueryGenerationResult generationResult =
                    queryGeneratorService.generateQueries(request.getUserMessage(), schemas);

            List<QueryGeneratorService.GeneratedQuery> generatedQueries = generationResult.getQueries();
            String detectedIntent = generationResult.getIntent();

            List<QueryGeneratorService.GeneratedQuery> validQueries = generatedQueries.stream()
                    .filter(QueryGeneratorService.GeneratedQuery::isValid)
                    .toList();

            if (validQueries.isEmpty()) {
                String reasons = generatedQueries.stream()
                        .filter(q -> !q.isValid())
                        .map(QueryGeneratorService.GeneratedQuery::getValidationError)
                        .collect(Collectors.joining("; "));

                sendErrorResponse(conversationId, user, "QUERY_GENERATION_FAILED",
                        "Could not generate a valid query for your request. " + reasons,
                        "Try rephrasing your question or specifying the table/column names more clearly.");
                return;
            }

            for (QueryGeneratorService.GeneratedQuery q : validQueries) {
                sendActivity(conversationId, user, AIActivityPhase.GENERATING_QUERIES,
                        "in_progress", "Generated query for connection " + q.getConnectionId() + ": " + q.getSql());
            }

            sendActivity(conversationId, user, AIActivityPhase.GENERATING_QUERIES,
                    "completed", "Generated " + validQueries.size() + " safe SELECT query/queries");

            // Phase 5: Executing queries
            sendActivity(conversationId, user, AIActivityPhase.EXECUTING_QUERIES,
                    "in_progress", "Executing queries against data sources");

            List<AnalyzeResponse.QueryResult> queryResults = new ArrayList<>();

            for (QueryGeneratorService.GeneratedQuery genQuery : validQueries) {
                DatabaseConnection conn = connections.stream()
                        .filter(c -> c.getId().equals(genQuery.getConnectionId()))
                        .findFirst()
                        .orElse(null);

                if (conn == null) continue;

                sendActivity(conversationId, user, AIActivityPhase.EXECUTING_QUERIES,
                        "in_progress", "Executing query on '" + conn.getName() + "'...");

                QueryExecutionService.ExecutionResult result = queryExecutionService.execute(conn, genQuery.getSql());

                if (result.isSuccess()) {
                    queryResults.add(AnalyzeResponse.QueryResult.builder()
                            .connectionId(conn.getId())
                            .connectionName(conn.getName())
                            .query(genQuery.getSql())
                            .data(result.getData())
                            .columns(result.getColumns())
                            .rowCount(result.getRowCount())
                            .build());

                    sendActivity(conversationId, user, AIActivityPhase.EXECUTING_QUERIES,
                            "in_progress", "Query on '" + conn.getName() + "' returned " +
                            result.getRowCount() + " row(s) in " + result.getExecutionTimeMs() + "ms");
                } else {
                    sendActivity(conversationId, user, AIActivityPhase.EXECUTING_QUERIES,
                            "in_progress", "Query on '" + conn.getName() + "' failed: " + result.getErrorMessage());
                }
            }

            sendActivity(conversationId, user, AIActivityPhase.EXECUTING_QUERIES,
                    "completed", "Query execution completed with " + queryResults.size() + " successful result(s)");

            // Phase 6: Preparing response
            sendActivity(conversationId, user, AIActivityPhase.PREPARING_RESPONSE,
                    "in_progress", "Preparing response and determining best visualization");

            String summary = buildSummary(request.getUserMessage(), queryResults);
            List<Map<String, Object>> allData = queryResults.stream()
                    .flatMap(qr -> qr.getData().stream())
                    .toList();
            String visualization = queryGeneratorService.suggestVisualization(detectedIntent, allData);

            sendActivity(conversationId, user, AIActivityPhase.PREPARING_RESPONSE,
                    "completed", "Response prepared with suggested visualization: " + visualization);

            // Store AI response
            storeAIResponse(conversationId, summary);

            // Phase 7: Final answer
            AnalyzeResponse response = AnalyzeResponse.success(
                    conversationId, summary, queryResults, visualization);

            sendActivity(conversationId, user, AIActivityPhase.COMPLETED,
                    "completed", "Analysis complete");

            messagingTemplate.convertAndSendToUser(
                    user.getId().toString(),
                    "/queue/ai/response",
                    response);

        } catch (Exception e) {
            log.error("Error processing analyze request: {}", e.getMessage(), e);
            sendErrorResponse(conversationId, user, "INTERNAL_ERROR",
                    "An unexpected error occurred while processing your request.",
                    "Please try again. If the problem persists, check your database connections.");
        }
    }

    private Long resolveConversation(AnalyzeRequest request, User user) {
        if (request.getConversationId() != null) {
            Conversation existing = conversationRepository.findByIdAndUserId(
                    request.getConversationId(), user.getId());
            if (existing != null) {
                return existing.getId();
            }
        }

        String connectionIdStr = request.getConnectionIds() != null
                ? request.getConnectionIds().stream().map(String::valueOf).collect(Collectors.joining(","))
                : "";

        String name = "AI Analysis - " + truncate(request.getUserMessage(), 50);

        Conversation conversation = Conversation.builder()
                .name(name)
                .connectionIds(connectionIdStr)
                .user(user.getId())
                .shared(false)
                .build();

        conversation = conversationRepository.save(conversation);
        return conversation.getId();
    }

    private void storeUserMessage(Long conversationId, String content) {
        Message message = Message.builder()
                .content(content)
                .sentByUser(true)
                .conversation(conversationId)
                .build();
        messageRepository.save(message);
    }

    private void storeAIResponse(Long conversationId, String content) {
        Message message = Message.builder()
                .content(content)
                .sentByUser(false)
                .conversation(conversationId)
                .build();
        messageRepository.save(message);
    }

    private List<DatabaseConnection> resolveConnections(List<Long> connectionIds, User user) {
        if (connectionIds == null || connectionIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<DatabaseConnection> connections = new ArrayList<>();
        for (Long connId : connectionIds) {
            DatabaseConnection conn = connectionRepository.findByIdAndUserId(connId, user.getId());
            if (conn != null) {
                connections.add(conn);
            } else {
                log.warn("Connection {} not found for user {}", connId, user.getId());
            }
        }
        return connections;
    }

    private String buildSummary(String userMessage, List<AnalyzeResponse.QueryResult> results) {
        if (results.isEmpty()) {
            return "No data was returned for your query: \"" + userMessage + "\". " +
                   "The query executed successfully but produced no matching results.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Results for your query: \"").append(truncate(userMessage, 100)).append("\"\n\n");

        for (AnalyzeResponse.QueryResult result : results) {
            sb.append("From ").append(result.getConnectionName()).append(": ");
            sb.append(result.getRowCount()).append(" row(s) returned");

            if (result.getRowCount() == 1 && result.getData().size() == 1) {
                Map<String, Object> row = result.getData().get(0);
                if (row.size() <= 3) {
                    sb.append(" - ");
                    row.forEach((k, v) -> sb.append(k).append(": ").append(v).append(", "));
                    sb.setLength(sb.length() - 2);
                }
            }
            sb.append("\n");
        }

        if (results.size() > 1) {
            sb.append("\nData was queried from ").append(results.size())
              .append(" separate data sources and presented independently.");
        }

        return sb.toString().trim();
    }

    private void sendActivity(Long conversationId, User user, AIActivityPhase phase,
                               String status, String message) {
        AIActivityMessage activity = AIActivityMessage.of(phase.getCode(), status, message, conversationId);

        messagingTemplate.convertAndSendToUser(
                user.getId().toString(),
                "/queue/ai/activity",
                activity);

        log.debug("AI Activity [{}]: {} - {}", phase.getCode(), status, message);
    }

    private void sendErrorResponse(Long conversationId, User user, String code,
                                    String message, String suggestion) {
        sendActivity(conversationId, user, AIActivityPhase.ERROR, "error", message);

        AnalyzeResponse errorResponse = AnalyzeResponse.error(conversationId, code, message, suggestion);

        messagingTemplate.convertAndSendToUser(
                user.getId().toString(),
                "/queue/ai/response",
                errorResponse);

        messagingTemplate.convertAndSendToUser(
                user.getId().toString(),
                "/queue/ai/error",
                errorResponse);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}

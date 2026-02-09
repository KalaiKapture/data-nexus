package com.datanexus.datanexus.controller;

import com.datanexus.datanexus.dto.websocket.AIActivityMessage;
import com.datanexus.datanexus.dto.websocket.AnalyzeRequest;
import com.datanexus.datanexus.dto.websocket.AnalyzeResponse;
import com.datanexus.datanexus.entity.User;
import com.datanexus.datanexus.service.ai.AIAnalystOrchestrator;
import com.datanexus.datanexus.service.ai.MultiSourceChatOrchestrator;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.security.Principal;
import java.time.Instant;

@Controller
@RequiredArgsConstructor
@Slf4j
@CrossOrigin("*")
public class AIAnalystWebSocketController {

    private final AIAnalystOrchestrator orchestrator;
    private final MultiSourceChatOrchestrator multiSourceOrchestrator;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/ai/analyze")
    public void analyzeData(@Payload AnalyzeRequest request,
            SimpMessageHeaderAccessor headerAccessor) {
        Principal principal = headerAccessor.getUser();

        if (principal == null) {
            log.warn("Received analyze request without authentication");
            return;
        }
        String wsUser = principal.getName();

        User user = extractUser(principal);
        if (user == null) {
            log.warn("Could not extract user from principal");
            return;
        }

        if (request.getUserMessage() == null || request.getUserMessage().isBlank()) {
            sendValidationError(user, request.getConversationId(), "Message cannot be empty");
            return;
        }

        if (request.getConnectionIds() == null || request.getConnectionIds().isEmpty()) {
            sendValidationError(user, request.getConversationId(),
                    "At least one connection ID is required");
            return;
        }

        log.info("Processing analyze request from user {} for conversation {} with AI provider: {}",
                user.getId(), request.getConversationId(), request.getAiProvider());

        // Route to appropriate orchestrator
        if (request.getAiProvider() != null && !request.getAiProvider().isEmpty()) {
            // Use new multi-source orchestrator with AI provider support
            multiSourceOrchestrator.processMessage(request, user, wsUser);
        } else {
            // Fallback to legacy orchestrator
            orchestrator.processAnalyzeRequest(request, user, wsUser);
        }
    }

    @MessageMapping("/ai/ping")
    @SendToUser("/queue/ai/pong")
    public AIActivityMessage ping(SimpMessageHeaderAccessor headerAccessor) {
        Principal principal = headerAccessor.getUser();
        String userId = principal != null ? principal.getName() : "unknown";

        return AIActivityMessage.builder()
                .phase("ping")
                .status("ok")
                .message("Connected as user " + userId)
                .timestamp(Instant.now())
                .build();
    }

    private User extractUser(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken authToken) {
            Object p = authToken.getPrincipal();
            if (p instanceof User) {
                return (User) p;
            }
        }
        return null;
    }

    private void sendValidationError(User user, Long conversationId, String message) {
        log.warn("Validation error for user {}: {}", user.getId(), message);

        AnalyzeResponse errorResponse = AnalyzeResponse.error(
                conversationId,
                "VALIDATION_ERROR",
                message,
                "Provide a non-empty message and at least one database connection ID.");

        messagingTemplate.convertAndSendToUser(
                user.getId().toString(),
                "/queue/ai/response",
                errorResponse);

        messagingTemplate.convertAndSendToUser(
                user.getId().toString(),
                "/queue/ai/error",
                errorResponse);
    }
}

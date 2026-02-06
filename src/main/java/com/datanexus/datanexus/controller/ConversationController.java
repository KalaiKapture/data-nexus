package com.datanexus.datanexus.controller;

import com.datanexus.datanexus.dto.ApiResponse;
import com.datanexus.datanexus.dto.conversation.*;
import com.datanexus.datanexus.entity.User;
import com.datanexus.datanexus.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getConversations(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "recent") String sort) {
        List<ConversationDto> conversations = conversationService.getUserConversations(user, limit, offset, sort);
        long total = conversationService.countUserConversations(user);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "conversations", conversations,
                "total", total,
                "limit", limit,
                "offset", offset
        )));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, ConversationDto>>> createConversation(
            @Valid @RequestBody CreateConversationRequest request,
            @AuthenticationPrincipal User user) {
        ConversationDto conversation = conversationService.createConversation(request, user);
        return new ResponseEntity<>(ApiResponse.success(Map.of("conversation", conversation)), HttpStatus.CREATED);
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<Map<String, ConversationDto>>> getConversation(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal User user) {
        ConversationDto conversation = conversationService.getConversation(conversationId, user);
        return ResponseEntity.ok(ApiResponse.success(Map.of("conversation", conversation)));
    }

    @PutMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<Map<String, ConversationDto>>> updateConversation(
            @PathVariable Long conversationId,
            @RequestBody UpdateConversationRequest request,
            @AuthenticationPrincipal User user) {
        ConversationDto conversation = conversationService.updateConversation(conversationId, request, user);
        return ResponseEntity.ok(ApiResponse.success(Map.of("conversation", conversation)));
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addMessage(
            @PathVariable Long conversationId,
            @Valid @RequestBody AddMessageRequest request,
            @AuthenticationPrincipal User user) {
        MessageDto message = conversationService.addMessage(conversationId, request, user);
        return new ResponseEntity<>(ApiResponse.success(Map.of("message", message)), HttpStatus.CREATED);
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMessages(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal User user) {
        List<MessageDto> messages = conversationService.getMessages(conversationId, user);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "messages", messages,
                "total", messages.size()
        )));
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal User user) {
        conversationService.deleteConversation(conversationId, user);
        return ResponseEntity.ok(ApiResponse.success("Conversation deleted successfully"));
    }

    @PostMapping("/{conversationId}/share")
    public ResponseEntity<ApiResponse<Map<String, Object>>> shareConversation(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal User user) {
        ConversationDto result = conversationService.shareConversation(conversationId, user);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "shareId", result.getShareId(),
                "conversation", result
        )));
    }

    @DeleteMapping("/{conversationId}/share")
    public ResponseEntity<ApiResponse<Void>> unshareConversation(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal User user) {
        conversationService.unshareConversation(conversationId, user);
        return ResponseEntity.ok(ApiResponse.success("Conversation is no longer shared"));
    }
}

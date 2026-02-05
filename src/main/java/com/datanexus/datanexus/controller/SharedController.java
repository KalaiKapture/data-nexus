package com.datanexus.datanexus.controller;

import com.datanexus.datanexus.dto.ApiResponse;
import com.datanexus.datanexus.dto.conversation.ConversationDto;
import com.datanexus.datanexus.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/shared")
@RequiredArgsConstructor
public class SharedController {

    private final ConversationService conversationService;

    @GetMapping("/{shareId}")
    public ResponseEntity<ApiResponse<Map<String, ConversationDto>>> getSharedConversation(
            @PathVariable String shareId) {
        ConversationDto conversation = conversationService.getSharedConversation(shareId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("conversation", conversation)));
    }
}

package com.datanexus.datanexus.controller;

import com.datanexus.datanexus.entity.Message;
import com.datanexus.datanexus.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for retrieving a saved Message by its DB id.
 *
 * GET /api/v1/dashboards/{messageId}
 *   → returns the full Message entity as JSON so the UI can process dashboardHtml etc.
 */
@RestController
@RequestMapping("/api/v1/dashboards")
@CrossOrigin("*")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final MessageRepository messageRepository;

    @GetMapping("/{messageId}")
    public ResponseEntity<Message> getMessage(@PathVariable("messageId") Long messageId) {
        Message message = messageRepository.findById(messageId);
        if (message == null) {
            log.warn("Message not found for id={}", messageId);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(message);
    }
}

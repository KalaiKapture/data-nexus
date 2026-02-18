package com.datanexus.datanexus.dto.websocket;

import lombok.*;

import java.time.Instant;
import java.util.List;

/**
 * Message sent from AI to user requesting clarification
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClarificationRequest {
    private Long conversationId;
    private String question;
    private String intent;
    private List<String> suggestedOptions;
    private Instant timestamp;
}

package com.datanexus.datanexus.dto.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AIActivityMessage {

    private String phase;
    private String status;
    private String message;
    private Long conversationId;
    private Instant timestamp;

    public static AIActivityMessage of(String phase, String status, String message, Long conversationId) {
        return AIActivityMessage.builder()
                .phase(phase)
                .status(status)
                .message(message)
                .conversationId(conversationId)
                .timestamp(Instant.now())
                .build();
    }
}

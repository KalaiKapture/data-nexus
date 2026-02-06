package com.datanexus.datanexus.dto.websocket;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyzeRequest {

    private String userMessage;
    private Long conversationId;
    private List<Long> connectionIds;
}

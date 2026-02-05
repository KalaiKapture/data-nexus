package com.datanexus.datanexus.dto.conversation;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConversationDto {

    private String id;
    private String name;
    private List<String> connectionIds;
    private List<MessageDto> messages;
    private int messageCount;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean isShared;
    private String shareId;
}

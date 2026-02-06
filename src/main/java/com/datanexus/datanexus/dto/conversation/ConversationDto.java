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

    private Long id;
    private String name;
    private List<Long> connectionIds;
    private List<MessageDto> messages;
    private int messageCount;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean isShared;
    private String shareId;
}

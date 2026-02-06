package com.datanexus.datanexus.dto.conversation;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageDto {

    private Long id;
    private String content;
    private boolean sentByUser;
    private Instant createdAt;
    private Instant updatedAt;
}

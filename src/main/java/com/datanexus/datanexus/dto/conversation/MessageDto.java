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

    private String id;
    private String role;
    private String content;
    private Object data;
    private Instant timestamp;
}

package com.datanexus.datanexus.dto.conversation;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateConversationRequest {

    private String name;
    private List<Long> connectionIds;
}

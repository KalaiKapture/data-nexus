package com.datanexus.datanexus.dto.conversation;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateConversationRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private List<String> connectionIds;
}

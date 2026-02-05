package com.datanexus.datanexus.dto.conversation;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AddMessageRequest {

    @NotBlank(message = "Role is required")
    private String role;

    @NotBlank(message = "Content is required")
    private String content;
}

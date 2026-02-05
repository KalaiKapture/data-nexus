package com.datanexus.datanexus.dto.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteQueryRequest {

    @NotBlank(message = "Query is required")
    private String query;

    @NotEmpty(message = "At least one connection ID is required")
    private List<String> connectionIds;

    private String conversationId;
}

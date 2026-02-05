package com.datanexus.datanexus.dto.query;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuerySuggestionsRequest {

    @NotEmpty(message = "At least one connection ID is required")
    private List<String> connectionIds;

    private String context;
}

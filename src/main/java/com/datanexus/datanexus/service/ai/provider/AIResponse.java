package com.datanexus.datanexus.service.ai.provider;

import com.datanexus.datanexus.service.datasource.DataRequest;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Response from AI provider
 */
@Getter
@Builder
public class AIResponse {
    private AIResponseType type;
    private String content;
    private List<DataRequest> dataRequests;

    // For clarification
    private String clarificationQuestion;
    private List<String> suggestedOptions;

    // Detected intent
    private String intent;
}

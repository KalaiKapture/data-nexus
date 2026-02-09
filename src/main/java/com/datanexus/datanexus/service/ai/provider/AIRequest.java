package com.datanexus.datanexus.service.ai.provider;

import com.datanexus.datanexus.service.datasource.schema.SourceSchema;
import com.datanexus.datanexus.entity.Message;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Request to AI provider
 */
@Getter
@Builder
public class AIRequest {
    private String userMessage;
    private List<SourceSchema> availableSchemas;
    private List<Message> conversationHistory;
    private Map<String, Object> preferences;
}

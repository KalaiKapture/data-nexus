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
    private String userId;
    private String conversationId;
    private boolean firstMessage;

    /**
     * When true, userMessage is sent directly to the AI without wrapping in
     * the schema/decision-logic prompt. Used for analysis and dashboard generation
     * where the prompt is already fully constructed.
     */
    private boolean rawPrompt;
    private String prompt;
}

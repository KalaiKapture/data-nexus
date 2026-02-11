package com.datanexus.datanexus.service.ai;

import lombok.Getter;

@Getter
public enum AIActivityPhase {

    UNDERSTANDING_INTENT("understanding_intent", "Understanding user intent"),
    MAPPING_DATA_SOURCES("mapping_data_sources", "Mapping intent to data sources"),
    ANALYZING_SCHEMAS("analyzing_schemas", "Analyzing schemas"),
    GENERATING_QUERIES("generating_queries", "Generating safe SELECT queries"),
    EXECUTING_QUERIES("executing_queries", "Executing queries"),
    AI_THINKING("ai_thinking", "AI is thinking..."),
    PREPARING_RESPONSE("preparing_response", "Preparing response"),
    COMPLETED("completed", "Final answer"),
    ERROR("error", "Error occurred");

    private final String code;
    private final String description;

    AIActivityPhase(String code, String description) {
        this.code = code;
        this.description = description;
    }
}

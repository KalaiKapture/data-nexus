package com.datanexus.datanexus.service.ai.provider;

/**
 * Type of AI response
 */
public enum AIResponseType {
    CLARIFICATION_NEEDED, // AI needs more information from user
    READY_TO_EXECUTE, // Queries/requests are ready to execute
    DIRECT_ANSWER // No data needed, AI answered directly from knowledge
}

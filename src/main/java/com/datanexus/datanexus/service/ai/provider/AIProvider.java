package com.datanexus.datanexus.service.ai.provider;

/**
 * Abstraction for AI providers (OpenAI, Gemini, Claude, etc.)
 */
public interface AIProvider {

    /**
     * Get provider name
     */
    String getName();

    /**
     * Main conversation method
     */
    AIResponse chat(AIRequest request);

    /**
     * Check if provider supports clarification
     */
    boolean supportsClarification();

    /**
     * Check if provider is configured and ready
     */
    boolean isConfigured();
}

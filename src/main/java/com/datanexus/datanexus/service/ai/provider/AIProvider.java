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
     * Main conversation method (non-streaming)
     */
    AIResponse chat(AIRequest request);

    /**
     * Streaming conversation method.
     * Sends text chunks to the handler as they arrive, then returns the full
     * parsed AIResponse.
     * Default implementation falls back to non-streaming chat().
     */
    default AIResponse streamChat(AIRequest request, StreamChunkHandler chunkHandler) {
        // Fallback: call non-streaming chat and send the full content as one chunk
        AIResponse response = chat(request);
        if (response.getContent() != null) {
            chunkHandler.onChunk(response.getContent());
        }
        return response;
    }

    /**
     * Check if provider supports clarification
     */
    boolean supportsClarification();

    /**
     * Check if provider is configured and ready
     */
    boolean isConfigured();
}

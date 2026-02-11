package com.datanexus.datanexus.service.ai.provider;

/**
 * Callback interface for receiving text chunks during AI streaming.
 */
@FunctionalInterface
public interface StreamChunkHandler {
    void onChunk(String textChunk);
}

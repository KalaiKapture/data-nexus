package com.datanexus.datanexus.service.ai.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * OpenAI AI provider implementation
 * Wraps existing OpenAI integration from QueryGeneratorService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAIProvider implements AIProvider {

    @Value("${ai.openai.api-key:}")
    private String apiKey;

    @Override
    public String getName() {
        return "openai";
    }

    @Override
    public boolean supportsClarification() {
        return true;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public AIResponse chat(AIRequest request) {
        if (!isConfigured()) {
            throw new IllegalStateException("OpenAI provider is not configured. Please set ai.openai.api-key");
        }

        // TODO: Implement similar to Gemini/Claude once we refactor
        // QueryGeneratorService
        // For now, return a simple response
        log.info("OpenAI provider called - implementation pending");

        return AIResponse.builder()
                .type(AIResponseType.READY_TO_EXECUTE)
                .content("OpenAI integration coming soon")
                .build();
    }
}

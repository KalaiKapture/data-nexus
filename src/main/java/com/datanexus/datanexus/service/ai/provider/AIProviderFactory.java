package com.datanexus.datanexus.service.ai.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating and retrieving AI providers
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AIProviderFactory {

    private final GeminiProvider geminiProvider;
    private final ClaudeProvider claudeProvider;
    private final OpenAIProvider openAIProvider;

    /**
     * Get provider by name
     */
    public AIProvider getProvider(String providerName) {
        if (providerName == null || providerName.isEmpty()) {
            return getDefaultProvider();
        }

        return switch (providerName.toLowerCase()) {
            case "gemini" -> geminiProvider;
            case "claude" -> claudeProvider;
            case "openai" -> openAIProvider;
            default -> {
                log.warn("Unknown provider: {}, using default", providerName);
                yield getDefaultProvider();
            }
        };
    }

    /**
     * Get default provider (first configured one)
     */
    public AIProvider getDefaultProvider() {
        if (geminiProvider.isConfigured())
            return geminiProvider;
        if (claudeProvider.isConfigured())
            return claudeProvider;
        if (openAIProvider.isConfigured())
            return openAIProvider;

        log.error("No AI providers configured!");
        throw new IllegalStateException("No AI providers are configured. Please set up at least one provider.");
    }

    /**
     * Get list of available configured providers
     */
    public List<String> getAvailableProviders() {
        List<String> available = new ArrayList<>();
        if (geminiProvider.isConfigured())
            available.add("gemini");
        if (claudeProvider.isConfigured())
            available.add("claude");
        if (openAIProvider.isConfigured())
            available.add("openai");
        return available;
    }
}

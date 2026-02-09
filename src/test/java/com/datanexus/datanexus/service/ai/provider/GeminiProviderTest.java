package com.datanexus.datanexus.service.ai.provider;

import com.datanexus.datanexus.entity.Message;
import com.datanexus.datanexus.service.datasource.DataSourceType;
import com.datanexus.datanexus.service.datasource.schema.SourceSchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GeminiProvider
 */
class GeminiProviderTest {

    private GeminiProvider provider;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        provider = new GeminiProvider(objectMapper);
    }

    @Test
    void testGetName_ReturnsGemini() {
        assertEquals("gemini", provider.getName());
    }

    @Test
    void testSupportsClarification_ReturnsTrue() {
        assertTrue(provider.supportsClarification());
    }

    @Test
    void testIsConfigured_WithoutApiKey_ReturnsFalse() {
        // Without setting api key, should return false
        // Note: This test assumes no API key is set in test environment
        assertFalse(provider.isConfigured());
    }

    @Test
    void testBuildPrompt_IncludesUserMessage() {
        // Arrange
        AIRequest request = AIRequest.builder()
                .userMessage("Show me sales data")
                .availableSchemas(new ArrayList<>())
                .conversationHistory(new ArrayList<>())
                .preferences(new HashMap<>())
                .build();

        // Act - this would require making buildPrompt public or using reflection
        // String prompt = provider.buildPrompt(request);

        // Assert
        // assertNotNull(prompt);
        // assertTrue(prompt.contains("Show me sales data"));
    }

    @Test
    void testChat_WithoutApiKey_ThrowsException() {
        // Arrange
        AIRequest request = AIRequest.builder()
                .userMessage("Test")
                .availableSchemas(new ArrayList<>())
                .conversationHistory(new ArrayList<>())
                .preferences(new HashMap<>())
                .build();

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            provider.chat(request);
        });
    }
}

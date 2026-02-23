package com.patternforge.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OllamaClient.
 */
class OllamaClientTest {

    private OllamaClient ollamaClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Point to a non-existent Ollama instance for unit tests
        ollamaClient = new OllamaClient(objectMapper, "http://localhost:99999", "llama3.3:latest");
    }

    @Test
    void isAvailable_shouldReturnFalse_whenOllamaNotRunning() {
        // When Ollama is not running, isAvailable should return false
        boolean available = ollamaClient.isAvailable();
        
        assertFalse(available, "Ollama should not be available on port 99999");
    }

    @Test
    void complete_shouldThrowException_whenOllamaNotRunning() {
        // When Ollama is not running, complete should throw OllamaApiException
        String prompt = "Test prompt";
        
        assertThrows(OllamaApiException.class, () -> {
            ollamaClient.complete(null, prompt);
        });
    }

    @Test
    void getDefaultModel_shouldReturnConfiguredModel() {
        String model = ollamaClient.getDefaultModel();
        
        assertEquals("llama3.3:latest", model);
    }

    @Test
    void getOllamaUrl_shouldReturnConfiguredUrl() {
        String url = ollamaClient.getOllamaUrl();
        
        assertEquals("http://localhost:99999", url);
    }
}

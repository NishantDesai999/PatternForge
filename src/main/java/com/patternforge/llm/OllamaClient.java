package com.patternforge.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Client for Ollama API - used for pattern extraction as primary option.
 * Falls back to Anthropic if Ollama is unavailable.
 */
@Component
@Slf4j
public class OllamaClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String ollamaUrl;
    private final String defaultModel;

    public OllamaClient(
            ObjectMapper objectMapper,
            @Value("${patternforge.ollama.url}") String ollamaUrl,
            @Value("${patternforge.extraction.ollama-model:llama3.3:latest}") String defaultModel
    ) {
        this.objectMapper = objectMapper;
        this.ollamaUrl = ollamaUrl;
        this.defaultModel = defaultModel;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Check if Ollama is available.
     */
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.debug("Ollama health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Complete a prompt using Ollama.
     *
     * @param model optional model name (uses default if null)
     * @param prompt the prompt to complete
     * @return the generated text
     * @throws OllamaApiException if the API call fails
     */
    public String complete(String model, String prompt) {
        String modelToUse = Objects.requireNonNullElse(model, defaultModel);
        
        log.debug("Calling Ollama API: model={}, promptLength={}", modelToUse, prompt.length());

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", modelToUse,
                    "prompt", prompt,
                    "stream", false,
                    "options", Map.of(
                            "temperature", 0.1,
                            "num_predict", 4000
                    )
            );

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaUrl + "/api/generate"))
                    .timeout(Duration.ofMinutes(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new OllamaApiException("Ollama API returned status " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String generatedText = root.path("response").asText();

            if (generatedText.isBlank()) {
                throw new OllamaApiException("Ollama returned empty response");
            }

            log.debug("Ollama API success: responseLength={}", generatedText.length());
            return generatedText;

        } catch (OllamaApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ollama API call failed", e);
            throw new OllamaApiException("Ollama API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get the default model name.
     */
    public String getDefaultModel() {
        return defaultModel;
    }

    /**
     * Get the Ollama URL.
     */
    public String getOllamaUrl() {
        return ollamaUrl;
    }
}

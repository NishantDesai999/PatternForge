package com.patternforge.extraction;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.patternforge.extraction.model.EmbeddingRequest;
import com.patternforge.extraction.model.EmbeddingResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for generating text embeddings via Ollama API.
 * Falls back to keyword search if Ollama unavailable.
 */
@Service
@Slf4j
public class EmbeddingService {
    
    private final String ollamaUrl;
    private final String ollamaModel;
    private final int timeout;
    private final AtomicBoolean ollamaAvailable;
    private final WebClient webClient;
    
    public EmbeddingService(
        @Value("${patternforge.ollama.url}") String ollamaUrl,
        @Value("${patternforge.ollama.model}") String ollamaModel,
        @Value("${patternforge.ollama.timeout}") int timeout,
        WebClient.Builder webClientBuilder
    ) {
        this.ollamaUrl = ollamaUrl;
        this.ollamaModel = ollamaModel;
        this.timeout = timeout;
        this.ollamaAvailable = new AtomicBoolean(true);
        this.webClient = webClientBuilder.baseUrl(ollamaUrl).build();
    }
    
    /**
     * Checks Ollama service availability on startup.
     * Sets ollamaAvailable to false if service unreachable.
     */
    @PostConstruct
    public void checkOllamaAvailability() {
        try {
            webClient.get()
                    .uri(ollamaUrl + "/api/tags")
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofMillis(2000))  // Quick 2-second timeout
                    .block();
            
            log.info("✓ Ollama embedding service available at {}", ollamaUrl);
            ollamaAvailable.set(true);
        } catch (Exception exception) {
            log.info("Ollama not available - using keyword search fallback (this is normal)");
            ollamaAvailable.set(false);
        }
    }
    
    /**
     * Generates embedding vector for given text.
     * Returns null if Ollama unavailable (triggers keyword fallback).
     *
     * @param text The text to embed
     * @return Embedding vector or null if unavailable
     */
    public float[] generateEmbedding(String text) {
        if (!ollamaAvailable.get()) {
            return null;
        }
        
        try {
            EmbeddingRequest request = new EmbeddingRequest(ollamaModel, text);
            EmbeddingResponse response = webClient.post()
                .uri("/api/embeddings")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EmbeddingResponse.class)
                .timeout(Duration.ofMillis(timeout))
                .block();
            
            return Objects.nonNull(response) ? response.embedding() : null;
        } catch (Exception exception) {
            log.debug("Embedding unavailable - using keyword search");
            ollamaAvailable.set(false);
            return null;
        }
    }
    
    /**
     * Checks if Ollama service is currently available.
     *
     * @return true if available, false otherwise
     */
    public boolean isAvailable() {
        return ollamaAvailable.get();
    }
}

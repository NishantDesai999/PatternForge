package com.patternforge.extraction.model;

/**
 * Request payload for Ollama embedding API.
 *
 * @param model The embedding model to use (e.g., "nomic-embed-text")
 * @param prompt The text to generate embeddings for
 */
public record EmbeddingRequest(String model, String prompt) {
}

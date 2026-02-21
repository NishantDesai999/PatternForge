package com.patternforge.extraction.model;

/**
 * Response payload from Ollama embedding API.
 *
 * @param embedding The generated embedding vector
 */
public record EmbeddingResponse(float[] embedding) {
}

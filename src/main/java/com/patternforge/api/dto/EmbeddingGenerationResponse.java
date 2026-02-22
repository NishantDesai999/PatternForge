package com.patternforge.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body for POST /api/patterns/admin/generate-embeddings.
 *
 * @param status        "success" or "error"
 * @param totalPatterns total number of patterns processed
 * @param successCount  patterns for which embeddings were successfully generated
 * @param failureCount  patterns for which embedding generation failed
 * @param message       human-readable summary
 */
public record EmbeddingGenerationResponse(
        String status,
        @JsonProperty("total_patterns") int totalPatterns,
        @JsonProperty("success_count") int successCount,
        @JsonProperty("failure_count") int failureCount,
        String message) {}

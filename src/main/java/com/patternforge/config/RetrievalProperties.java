package com.patternforge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for pattern retrieval and search tuning.
 * Controls similarity thresholds, result caps, and total response limits
 * to keep LLM context token usage under control.
 */
@Component
@ConfigurationProperties(prefix = "patternforge.retrieval")
@Data
public class RetrievalProperties {

    /**
     * Minimum cosine similarity score (0.0–1.0) for vector search results.
     * Patterns below this threshold are dropped even if they rank in the top-K.
     */
    private double similarityThreshold = 0.3;

    /**
     * Maximum number of project-specific (conversational) patterns to return.
     */
    private int maxProjectPatterns = 5;

    /**
     * Hard upper limit on total patterns in a single query response.
     * When exceeded, lowest-relevance patterns are trimmed first.
     */
    private int maxTotalPatterns = 20;
}

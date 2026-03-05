package com.patternforge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the pattern retrieval pipeline.
 *
 * <p>Controls how many patterns are returned per query category and what the minimum
 * similarity threshold is for vector search results. These limits exist to prevent
 * the response payload from ballooning — returning every global standard on every
 * query defeats the purpose of context-aware retrieval and drives up LLM token costs.
 */
@Component
@ConfigurationProperties(prefix = "patternforge.retrieval")
@Data
public class RetrievalProperties {

    /**
     * Maximum number of global standard patterns included in a query response.
     * Patterns are selected by success_rate DESC so the most reliable ones come first.
     * Default: 5 — sufficient context without flooding the LLM with irrelevant standards.
     */
    private int maxGlobalStandards = 5;

    /**
     * Maximum number of project-specific standard patterns included in a query response.
     * Default: 3.
     */
    private int maxProjectStandards = 3;

    /**
     * Minimum cosine similarity score [0.0, 1.0] required for a vector search result
     * to be included. Results below this threshold are discarded even if they are in
     * the top-K by distance ordering.
     * Default: 0.35 — filters out clearly unrelated patterns while keeping near matches.
     */
    private double minSimilarityThreshold = 0.35;

    /**
     * Maximum estimated token budget for patterns in a single query response.
     * Patterns are packed greedily by relevance score until this budget is exhausted.
     * Set to 0 to disable token budgeting (fall back to count-based caps only).
     * Default: 8000 — keeps pattern context well within a single LLM message.
     */
    private int maxContextTokens = 8000;
}

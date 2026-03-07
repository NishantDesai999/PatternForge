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

    // ── Dynamic Context Pruning (DCP) properties ──────────────────────────────

    /**
     * When the caller supplies {@code remainingContextTokens}, this fraction of that
     * value becomes the effective token budget (capped at {@link #maxContextTokens}).
     * E.g. 0.30 means "use at most 30 % of the agent's remaining context for patterns".
     * Default: 0.30.
     */
    private double adaptiveContextFraction = 0.30;

    /**
     * Minimum relevance score [0.0, 1.0] required to re-inject a pattern that has
     * already been sent in the current conversation.  Patterns below this threshold
     * are skipped on subsequent turns to avoid redundant context.
     * Default: 0.80.
     */
    private double reinjectionScoreThreshold = 0.80;

    /**
     * Cosine similarity between the current task embedding and the previous turn's
     * embedding below which a "task shift" is declared.  When a shift is detected
     * the response includes a {@code dropPatternIds} list so the agent can evict
     * stale context.
     * Default: 0.40.
     */
    private double taskShiftThreshold = 0.40;
}

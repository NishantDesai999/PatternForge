package com.patternforge.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Metadata block included in every pattern query response.
 *
 * @param patternsRetrieved total number of patterns returned after deduplication and budget fitting
 * @param taskType          classified task type (fix_test, add_endpoint, fix_bug, etc.)
 * @param searchStrategy    "semantic" when Ollama is available, "keyword" when falling back
 * @param estimatedTokens   estimated LLM token cost of all returned patterns (0 when budgeting disabled)
 * @param tokenBudget       configured max token budget (0 when budgeting disabled)
 */
public record QueryMetadata(
        @JsonProperty("patterns_retrieved") int patternsRetrieved,
        @JsonProperty("task_type") String taskType,
        @JsonProperty("search_strategy") String searchStrategy,
        @JsonProperty("estimated_tokens") int estimatedTokens,
        @JsonProperty("token_budget") int tokenBudget) {

    /**
     * Backward-compatible constructor without token fields.
     */
    public QueryMetadata(int patternsRetrieved, String taskType, String searchStrategy) {
        this(patternsRetrieved, taskType, searchStrategy, 0, 0);
    }
}

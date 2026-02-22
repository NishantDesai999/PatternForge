package com.patternforge.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Metadata block included in every pattern query response.
 *
 * @param patternsRetrieved total number of patterns returned after deduplication
 * @param taskType          classified task type (fix_test, add_endpoint, fix_bug, etc.)
 * @param searchStrategy    "semantic" when Ollama is available, "keyword" when falling back
 */
public record QueryMetadata(
        @JsonProperty("patterns_retrieved") int patternsRetrieved,
        @JsonProperty("task_type") String taskType,
        @JsonProperty("search_strategy") String searchStrategy) {}

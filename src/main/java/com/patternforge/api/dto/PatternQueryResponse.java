package com.patternforge.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.patternforge.retrieval.model.RetrievedPattern;
import com.patternforge.workflow.model.WorkflowResponse;

import java.util.List;

/**
 * Response body for POST /api/patterns/query.
 *
 * @param patterns      retrieved patterns ordered by relevance (global standards first)
 * @param workflow      resolved workflow for the task (project-specific, global, or generated)
 * @param metadata      query metadata — pattern count, task type, search strategy used
 * @param dropPatternIds pattern IDs the agent should evict from its context; non-null only when a
 *                       task shift is detected mid-conversation (requires {@code conversationId} and
 *                       Ollama embeddings on both turns); empty list when no shift detected
 */
public record PatternQueryResponse(
        List<RetrievedPattern> patterns,
        WorkflowResponse workflow,
        QueryMetadata metadata,
        @JsonProperty("drop_pattern_ids") List<String> dropPatternIds) {}

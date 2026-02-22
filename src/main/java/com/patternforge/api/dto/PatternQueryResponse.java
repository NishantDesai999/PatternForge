package com.patternforge.api.dto;

import com.patternforge.retrieval.model.RetrievedPattern;
import com.patternforge.workflow.model.WorkflowResponse;

import java.util.List;

/**
 * Response body for POST /api/patterns/query.
 *
 * @param patterns  retrieved patterns ordered by relevance (global standards first)
 * @param workflow  resolved workflow for the task (project-specific, global, or generated)
 * @param metadata  query metadata — pattern count, task type, search strategy used
 */
public record PatternQueryResponse(
        List<RetrievedPattern> patterns,
        WorkflowResponse workflow,
        QueryMetadata metadata) {}

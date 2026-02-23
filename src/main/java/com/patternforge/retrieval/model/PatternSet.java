package com.patternforge.retrieval.model;

import java.util.List;

import lombok.Builder;
import lombok.Value;

/**
 * Complete pattern set for a task.
 * Combines retrieved patterns from semantic search, mandatory patterns, and session-specific patterns.
 */
@Value
@Builder
public class PatternSet {
    
    private List<RetrievedPattern> retrievedPatterns;
    private List<RetrievedPattern> mandatoryPatterns;
    private List<Object> sessionPatterns;
}

package com.patternforge.retrieval.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Complete pattern set for a task.
 * Combines retrieved patterns from semantic search, mandatory patterns, and session-specific patterns.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatternSet {
    
    private List<RetrievedPattern> retrievedPatterns;
    private List<RetrievedPattern> mandatoryPatterns;
    private List<Object> sessionPatterns;
}

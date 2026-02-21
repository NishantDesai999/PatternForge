package com.patternforge.retrieval.model;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a pattern retrieved from semantic search.
 * Contains pattern metadata, code examples, and relevance metrics for task-specific retrieval.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievedPattern {
    
    private String patternId;
    private String patternName;
    private String title;
    private String description;
    private String category;
    private String whenToUse;
    private Map<String, String> codeExamples;
    private double relevanceScore;
    private double successRate;
    private String workflowId;
    private Map<String, Object> patternData;
}

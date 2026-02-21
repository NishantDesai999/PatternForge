package com.patternforge.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO for capturing conversational patterns from user interactions.
 * Represents patterns taught or corrected during conversations.
 */
@Data
@Builder
public class PatternCaptureRequest {
    
    /**
     * Human-readable description of the pattern (required).
     */
    private String description;
    
    /**
     * Code example demonstrating the pattern (optional).
     */
    private String codeExample;
    
    /**
     * Rationale explaining why this pattern should be followed (optional).
     */
    private String rationale;
    
    /**
     * Source of the pattern.
     * Valid values: "user_explicit", "user_correction", "agent_observation"
     */
    private String source;
    
    /**
     * Project path where pattern was learned (required).
     */
    private String projectPath;
    
    /**
     * Conversation ID for tracing (optional).
     */
    private String conversationId;
    
    /**
     * Confidence score for the pattern (default: 0.95).
     */
    @Builder.Default
    private Double confidence = 0.95;
    
    /**
     * Programming languages this pattern applies to (optional).
     */
    private List<String> languages;
}

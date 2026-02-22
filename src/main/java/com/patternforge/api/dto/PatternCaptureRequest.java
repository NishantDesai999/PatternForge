package com.patternforge.api.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * DTO for capturing conversational patterns from user interactions.
 * Represents patterns taught or corrected during conversations.
 */
@Value
@Builder
public class PatternCaptureRequest {
    
    /**
     * Human-readable description of the pattern (required).
     */
    String description;
    
    /**
     * Code example demonstrating the pattern (optional).
     */
    String codeExample;
    
    /**
     * Rationale explaining why this pattern should be followed (optional).
     */
    String rationale;
    
    /**
     * Source of the pattern.
     * Valid values: "user_explicit", "user_correction", "agent_observation"
     */
    String source;
    
    /**
     * Project path where pattern was learned (required).
     */
    String projectPath;
    
    /**
     * Conversation ID for tracing (optional).
     */
    String conversationId;
    
    /**
     * Confidence score for the pattern (default: 0.95).
     */
    @Builder.Default
    Double confidence = 0.95;
    
    /**
     * Programming languages this pattern applies to (optional).
     */
    List<String> languages;
}

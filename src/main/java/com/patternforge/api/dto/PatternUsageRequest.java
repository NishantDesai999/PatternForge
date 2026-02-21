package com.patternforge.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Data Transfer Object for pattern usage tracking requests.
 * Used when OpenCode agents report pattern usage results.
 */
@Data
@Builder
public class PatternUsageRequest {
    
    /**
     * The ID of the pattern that was used.
     */
    private UUID patternId;
    
    /**
     * The absolute path to the project where the pattern was used.
     */
    private String projectPath;
    
    /**
     * The type of task (e.g., "refactoring", "new_feature", "bug_fix").
     */
    private String taskType;
    
    /**
     * Optional description of the task.
     */
    private String taskDescription;
    
    /**
     * Whether the pattern was successfully applied.
     */
    private Boolean success;
    
    /**
     * Optional code quality score from 0.0 to 1.0.
     */
    private Double codeQualityScore;
    
    /**
     * Optional number of iterations needed to complete the task.
     */
    private Integer iterationsNeeded;
}

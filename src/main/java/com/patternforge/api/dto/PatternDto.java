package com.patternforge.api.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object for Pattern API responses.
 * Decouples REST API from jOOQ database records to avoid Jackson serialization issues.
 */
@Value
@Builder
public class PatternDto {
    
    private UUID patternId;
    private String patternName;
    private String title;
    private String description;
    private String category;
    private String scope;
    private List<String> languages;
    private String whenToUse;
    private Map<String, String> codeExamples;
    private Double successRate;
    private Integer usageCount;
    private Boolean isGlobalStandard;
}

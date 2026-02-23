package com.patternforge.retrieval.model;

import java.util.List;

import lombok.Builder;
import lombok.Value;

/**
 * Context information for a specific development task.
 * Used to retrieve relevant patterns based on task type, components, and technical requirements.
 */
@Value
@Builder
public class TaskContext {
    
    private String description;
    private String taskType;
    private List<String> components;
    private List<String> concerns;
    private String language;
    private String framework;
}

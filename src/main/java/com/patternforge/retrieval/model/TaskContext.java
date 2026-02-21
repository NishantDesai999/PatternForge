package com.patternforge.retrieval.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Context information for a specific development task.
 * Used to retrieve relevant patterns based on task type, components, and technical requirements.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskContext {
    
    private String description;
    private String taskType;
    private List<String> components;
    private List<String> concerns;
    private String language;
    private String framework;
}

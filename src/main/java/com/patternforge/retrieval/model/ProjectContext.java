package com.patternforge.retrieval.model;

import java.util.List;

import lombok.Builder;
import lombok.Value;

/**
 * Project-level context information.
 * Provides metadata about the project for pattern retrieval and workflow customization.
 */
@Value
@Builder
public class ProjectContext {
    
    private String projectId;
    private String projectName;
    private String projectPath;
    private String language;
    private String framework;
    private List<String> techStack;
}

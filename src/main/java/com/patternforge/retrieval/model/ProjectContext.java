package com.patternforge.retrieval.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Project-level context information.
 * Provides metadata about the project for pattern retrieval and workflow customization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectContext {
    
    private String projectId;
    private String projectName;
    private String projectPath;
    private String language;
    private String framework;
    private List<String> techStack;
}

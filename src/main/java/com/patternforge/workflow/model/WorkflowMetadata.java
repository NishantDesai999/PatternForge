package com.patternforge.workflow.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata describing workflow characteristics for matching and selection.
 * Used by workflow engine to select appropriate workflows.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowMetadata {
    private String workflowName;
    private List<String> taskTypes;
    private List<String> languages;
    private List<String> frameworks;
    private int priority;
}

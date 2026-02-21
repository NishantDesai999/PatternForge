package com.patternforge.workflow.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a complete workflow response containing execution steps and quality gates.
 * Generated from workflow definitions and provided to execution engines.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowResponse {
    private String source;
    private boolean userDefined;
    private List<ExecutionStep> steps;
    private List<QualityGate> qualityGates;
    private String estimatedComplexity;
}

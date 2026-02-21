package com.patternforge.workflow.model;

import com.patternforge.retrieval.model.RetrievedPattern;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Individual step in workflow execution with pattern references and validation rules.
 * Supports both automated and manual approval steps.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionStep {
    private int step;
    private String action;
    private String tool;
    private String target;
    private List<String> patternReferences;
    private List<RetrievedPattern> resolvedPatterns;
    private String validation;
    private String command;
    private String agent;
    private boolean waitForUserApproval;
    private List<Map<String, Object>> applyPatterns;
}

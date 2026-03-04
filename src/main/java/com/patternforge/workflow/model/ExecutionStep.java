package com.patternforge.workflow.model;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Individual step in workflow execution with pattern references and validation rules.
 * Supports both automated and manual approval steps.
 *
 * <p>Pattern data is intentionally NOT embedded here. The full patterns are already
 * present in the enclosing {@code PatternQueryResponse.patterns} list. Referencing them
 * by name avoids duplicating potentially large code-example payloads in every step,
 * which was a primary driver of excessive LLM token usage.
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
    /** Pattern names resolved for this step. Cross-reference with response-level patterns list. */
    private List<String> resolvedPatternNames;
    private String validation;
    private String command;
    private String agent;
    private boolean waitForUserApproval;
    private List<Map<String, Object>> applyPatterns;
}

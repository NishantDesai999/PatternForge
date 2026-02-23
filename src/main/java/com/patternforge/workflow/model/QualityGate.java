package com.patternforge.workflow.model;

import lombok.Builder;
import lombok.Value;

/**
 * Quality gate definition for workflow validation.
 * Blocks execution if gate fails and isBlocking is true.
 */
@Value
@Builder
public class QualityGate {
    private String gateName;
    private String gateType;
    private String command;
    private boolean isBlocking;
    private String description;
}

package com.patternforge.workflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Quality gate definition for workflow validation.
 * Blocks execution if gate fails and isBlocking is true.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityGate {
    private String gateName;
    private String gateType;
    private String command;
    private boolean isBlocking;
    private String description;
}

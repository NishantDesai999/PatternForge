package com.patternforge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configuration properties for workflow resolution.
 * Replaces the scalar {@code global-workflows-path} @Value field in WorkflowResolver
 * with ordered lists supporting multiple agent tools simultaneously.
 */
@Component
@ConfigurationProperties(prefix = "patternforge.workflow")
@Data
public class WorkflowProperties {

    /**
     * Whether user-defined workflows (project or global) are considered.
     */
    private boolean userWorkflowsEnabled = true;

    /**
     * When true, workflow files are read fresh on every query (no caching).
     */
    private boolean reloadOnQuery = true;

    /**
     * Project-relative subdirectories checked in order; first match wins.
     * Supports multiple agent conventions: OpenCode, Claude Code, generic agents.
     */
    private List<String> projectWorkflowDirs = List.of(
            ".opencode/workflows",
            ".claude/workflows",
            ".cursor/workflows",
            ".windsurf/workflows",
            ".aider/workflows",
            ".agent/workflows"
    );

    /**
     * Absolute global workflow paths checked in order after project dirs.
     * Typically user home config dirs for each agent tool.
     */
    private List<String> globalWorkflowPaths = List.of();
}

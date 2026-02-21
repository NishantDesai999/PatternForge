package com.patternforge.api.dto;

import lombok.Data;

/**
 * Request body for the POST /api/patterns/extract endpoint.
 */
@Data
public class PatternExtractionRequest {

    /**
     * Absolute path to the rules/config file to extract patterns from
     * (e.g., AGENTS.md, CLAUDE.md). Required.
     */
    private String filePath;

    /**
     * Absolute path to the project root. Optional — used for tagging
     * the extracted patterns with their source project.
     */
    private String projectPath;
}

package com.patternforge.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Success response body for POST /api/patterns/capture.
 *
 * @param status      always "success" for this response type
 * @param patternId   UUID of the newly created conversational pattern
 * @param projectId   UUID of the project the pattern is associated with
 * @param projectName human-readable name of the project
 * @param message     human-readable confirmation
 */
public record PatternCaptureResponse(
        String status,
        @JsonProperty("pattern_id") UUID patternId,
        @JsonProperty("project_id") UUID projectId,
        @JsonProperty("project_name") String projectName,
        String message) {}

package com.patternforge.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Success response body for POST /api/patterns/usage.
 *
 * @param status      always "success" for this response type
 * @param usageId     UUID of the created usage record
 * @param successRate updated pattern success rate after recording this usage
 * @param message     human-readable confirmation
 */
public record PatternUsageResponse(
        String status,
        @JsonProperty("usage_id") String usageId,
        @JsonProperty("success_rate") double successRate,
        String message) {}

package com.patternforge.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body for POST /api/patterns/admin/promote-patterns.
 *
 * @param status             "success" or "error"
 * @param promotedToProject  count of conversational patterns promoted to project standard
 * @param promotedToGlobal   count of project standards promoted to global standard
 * @param totalPromoted      total promotions across both levels
 * @param message            human-readable summary
 */
public record PromotionResponse(
        String status,
        @JsonProperty("promoted_to_project") int promotedToProject,
        @JsonProperty("promoted_to_global") int promotedToGlobal,
        @JsonProperty("total_promoted") int totalPromoted,
        String message) {}

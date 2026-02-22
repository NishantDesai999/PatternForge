package com.patternforge.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Success response body for POST /api/patterns/extract.
 *
 * @param status            always "success" for this response type
 * @param patternsExtracted number of patterns extracted and upserted
 * @param patterns          the extracted pattern DTOs
 * @param sourceFile        path of the file that was processed
 */
public record PatternExtractionResponse(
        String status,
        @JsonProperty("patterns_extracted") int patternsExtracted,
        List<PatternDto> patterns,
        @JsonProperty("source_file") String sourceFile) {}

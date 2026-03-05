package com.patternforge.retrieval;

import com.patternforge.retrieval.model.RetrievedPattern;

import java.util.Map;
import java.util.Objects;

/**
 * Estimates the LLM token cost of a {@link RetrievedPattern} when serialized to JSON.
 *
 * <p>Uses a simple character-count heuristic (~4 characters per token for English text
 * and JSON structural overhead). This avoids pulling in a tokenizer library while being
 * accurate enough for budgeting decisions — the goal is to prevent 10x overflows, not
 * to be exact to the token.
 */
public final class TokenEstimator {

    private static final double CHARS_PER_TOKEN = 4.0;
    private static final int JSON_OVERHEAD_CHARS = 120;

    private TokenEstimator() {}

    /**
     * Estimates token count for a single pattern when serialized to JSON.
     */
    public static int estimate(RetrievedPattern pattern) {
        if (Objects.isNull(pattern)) {
            return 0;
        }

        int chars = JSON_OVERHEAD_CHARS;
        chars += safeLength(pattern.getPatternId());
        chars += safeLength(pattern.getPatternName());
        chars += safeLength(pattern.getTitle());
        chars += safeLength(pattern.getDescription());
        chars += safeLength(pattern.getCategory());
        chars += safeLength(pattern.getWhenToUse());
        chars += safeLength(pattern.getWorkflowId());
        chars += estimateMapChars(pattern.getCodeExamples());
        chars += estimatePatternDataChars(pattern.getPatternData());

        return Math.max(1, (int) Math.ceil(chars / CHARS_PER_TOKEN));
    }

    private static int safeLength(String value) {
        return Objects.isNull(value) ? 0 : value.length();
    }

    private static int estimateMapChars(Map<String, String> map) {
        if (Objects.isNull(map) || map.isEmpty()) {
            return 0;
        }
        int chars = 0;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            chars += safeLength(entry.getKey()) + safeLength(entry.getValue()) + 6;
        }
        return chars;
    }

    private static int estimatePatternDataChars(Map<String, Object> map) {
        if (Objects.isNull(map) || map.isEmpty()) {
            return 0;
        }
        int chars = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            chars += safeLength(entry.getKey()) + 6;
            if (Objects.nonNull(entry.getValue())) {
                chars += entry.getValue().toString().length();
            }
        }
        return chars;
    }
}

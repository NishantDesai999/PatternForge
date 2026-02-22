package com.patternforge.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for VectorUtils — float[] <-> PostgreSQL vector string conversion.
 */
class VectorUtilsTest {

    // ==================== toPostgresVector ====================

    @Test
    void shouldConvertSingleElementArray() {
        float[] embedding = {0.5f};
        String result = VectorUtils.toPostgresVector(embedding);
        assertThat(result).isEqualTo("[0.5]");
    }

    @Test
    void shouldConvertMultiElementArray() {
        float[] embedding = {0.1f, 0.2f, 0.3f};
        String result = VectorUtils.toPostgresVector(embedding);
        assertThat(result).isEqualTo("[0.1,0.2,0.3]");
    }

    @Test
    void shouldReturnEmptyBracketsForNullArray() {
        String result = VectorUtils.toPostgresVector(null);
        assertThat(result).isEqualTo("[]");
    }

    @Test
    void shouldReturnEmptyBracketsForEmptyArray() {
        String result = VectorUtils.toPostgresVector(new float[0]);
        assertThat(result).isEqualTo("[]");
    }

    @Test
    void shouldHandleNegativeValues() {
        float[] embedding = {-0.5f, -1.0f, 0.75f};
        String result = VectorUtils.toPostgresVector(embedding);
        assertThat(result).isEqualTo("[-0.5,-1.0,0.75]");
    }

    @Test
    void shouldHandleZeroValues() {
        float[] embedding = {0.0f, 0.0f};
        String result = VectorUtils.toPostgresVector(embedding);
        assertThat(result).isEqualTo("[0.0,0.0]");
    }

    @Test
    void shouldHandleLargeArray() {
        float[] embedding = new float[768];
        for (int i = 0; i < 768; i++) {
            embedding[i] = (float) i / 768;
        }
        String result = VectorUtils.toPostgresVector(embedding);
        assertThat(result).startsWith("[");
        assertThat(result).endsWith("]");
        // Count commas — should be 767 separating 768 elements
        long commaCount = result.chars().filter(c -> c == ',').count();
        assertThat(commaCount).isEqualTo(767);
    }

    // ==================== fromPostgresVector ====================

    @Test
    void shouldParseSingleElement() {
        float[] result = VectorUtils.fromPostgresVector("[0.5]");
        assertThat(result).hasSize(1);
        assertThat(result[0]).isCloseTo(0.5f, within(0.001f));
    }

    @Test
    void shouldParseMultipleElements() {
        float[] result = VectorUtils.fromPostgresVector("[0.1,0.2,0.3]");
        assertThat(result).hasSize(3);
        assertThat(result[0]).isCloseTo(0.1f, within(0.001f));
        assertThat(result[1]).isCloseTo(0.2f, within(0.001f));
        assertThat(result[2]).isCloseTo(0.3f, within(0.001f));
    }

    @Test
    void shouldReturnEmptyArrayForNull() {
        float[] result = VectorUtils.fromPostgresVector(null);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyArrayForBlankString() {
        float[] result = VectorUtils.fromPostgresVector("   ");
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyArrayForEmptyString() {
        float[] result = VectorUtils.fromPostgresVector("");
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyArrayForEmptyBrackets() {
        float[] result = VectorUtils.fromPostgresVector("[]");
        assertThat(result).isEmpty();
    }

    @Test
    void shouldParseNegativeValues() {
        float[] result = VectorUtils.fromPostgresVector("[-0.5,-1.0,0.75]");
        assertThat(result).hasSize(3);
        assertThat(result[0]).isCloseTo(-0.5f, within(0.001f));
        assertThat(result[1]).isCloseTo(-1.0f, within(0.001f));
        assertThat(result[2]).isCloseTo(0.75f, within(0.001f));
    }

    @Test
    void shouldHandleSpacesAroundBrackets() {
        float[] result = VectorUtils.fromPostgresVector("  [0.1,0.2]  ");
        assertThat(result).hasSize(2);
        assertThat(result[0]).isCloseTo(0.1f, within(0.001f));
    }

    // ==================== Round-trip ====================

    @Test
    void shouldRoundTripCorrectly() {
        float[] original = {0.1f, 0.5f, -0.3f, 1.0f, 0.0f};
        String vectorString = VectorUtils.toPostgresVector(original);
        float[] parsed = VectorUtils.fromPostgresVector(vectorString);

        assertThat(parsed).hasSize(original.length);
        for (int i = 0; i < original.length; i++) {
            assertThat(parsed[i]).isCloseTo(original[i], within(0.0001f));
        }
    }

    @Test
    void shouldRoundTripEmptyArray() {
        float[] original = new float[0];
        String vectorString = VectorUtils.toPostgresVector(original);
        float[] parsed = VectorUtils.fromPostgresVector(vectorString);
        assertThat(parsed).isEmpty();
    }
}

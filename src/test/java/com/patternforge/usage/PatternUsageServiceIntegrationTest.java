package com.patternforge.usage;

import com.patternforge.AbstractIntegrationTest;
import com.patternforge.api.dto.PatternUsageRequest;
import com.patternforge.jooq.tables.records.PatternsRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.patternforge.jooq.Tables.PATTERN_USAGE;
import static com.patternforge.jooq.Tables.PATTERNS;
import static com.patternforge.jooq.Tables.PROJECTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Integration tests for PatternUsageService — usage recording and metrics.
 */
class PatternUsageServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PatternUsageService patternUsageService;

    // ==================== recordUsage ====================

    @Test
    void shouldRecordUsageSuccessfully() {
        // Arrange
        UUID patternId = insertPattern("test-pattern", "Test Pattern");
        PatternUsageRequest request = buildRequest(patternId, "/test/project", "fix_bug", true);

        // Act
        UUID usageId = patternUsageService.recordUsage(request);

        // Assert
        assertThat(usageId).isNotNull();

        Long count = dsl.selectCount().from(PATTERN_USAGE)
                .where(PATTERN_USAGE.PATTERN_ID.eq(patternId))
                .fetchOne(0, Long.class);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void shouldCreateProjectWhenProjectPathNotFound() {
        // Arrange
        UUID patternId = insertPattern("pat", "Pat");
        PatternUsageRequest request = buildRequest(patternId, "/new/project/path", "add_feature", true);

        // Act
        patternUsageService.recordUsage(request);

        // Assert — project was auto-created
        Long projectCount = dsl.selectCount().from(PROJECTS)
                .where(PROJECTS.PROJECT_PATH.eq("/new/project/path"))
                .fetchOne(0, Long.class);
        assertThat(projectCount).isEqualTo(1L);
    }

    @Test
    void shouldReuseExistingProject() {
        // Arrange
        UUID patternId = insertPattern("pat2", "Pat Two");
        String projectPath = "/existing/project";

        patternUsageService.recordUsage(buildRequest(patternId, projectPath, "fix_bug", true));
        patternUsageService.recordUsage(buildRequest(patternId, projectPath, "add_feature", false));

        // Assert — still only one project
        Long projectCount = dsl.selectCount().from(PROJECTS)
                .where(PROJECTS.PROJECT_PATH.eq(projectPath))
                .fetchOne(0, Long.class);
        assertThat(projectCount).isEqualTo(1L);

        Long usageCount = dsl.selectCount().from(PATTERN_USAGE)
                .where(PATTERN_USAGE.PATTERN_ID.eq(patternId))
                .fetchOne(0, Long.class);
        assertThat(usageCount).isEqualTo(2L);
    }

    @Test
    void shouldUpdatePatternMetricsAfterRecording() {
        // Arrange
        UUID patternId = insertPattern("metrics-pat", "Metrics Pattern");

        patternUsageService.recordUsage(buildRequest(patternId, "/proj/a", "fix_bug", true));
        patternUsageService.recordUsage(buildRequest(patternId, "/proj/b", "fix_bug", false));

        // Assert — usage_count should be 2
        PatternsRecord pattern = dsl.selectFrom(PATTERNS)
                .where(PATTERNS.PATTERN_ID.eq(patternId))
                .fetchOne();
        assertThat(pattern).isNotNull();
        assertThat(pattern.getUsageCount()).isEqualTo(2);
        // success_rate: 1 success out of 2 = 0.5
        assertThat(pattern.getSuccessRate()).isCloseTo(0.5, within(0.01));
    }

    // ==================== calculateSuccessRate ====================

    @Test
    void shouldReturnNullForNullPatternId() {
        Double rate = patternUsageService.calculateSuccessRate(null);
        assertThat(rate).isNull();
    }

    @Test
    void shouldReturnNullWhenNoUsageRecordsExist() {
        UUID patternId = UUID.randomUUID();
        Double rate = patternUsageService.calculateSuccessRate(patternId);
        // No usage records → null (division by zero guarded in DB)
        assertThat(rate).isNull();
    }

    @Test
    void shouldCalculateHundredPercentSuccessRate() {
        UUID patternId = insertPattern("always-success", "Always Success");
        patternUsageService.recordUsage(buildRequest(patternId, "/proj/x", "fix_bug", true));
        patternUsageService.recordUsage(buildRequest(patternId, "/proj/y", "fix_bug", true));

        Double rate = patternUsageService.calculateSuccessRate(patternId);

        assertThat(rate).isNotNull();
        assertThat(rate).isCloseTo(1.0, within(0.01));
    }

    @Test
    void shouldCalculateZeroSuccessRate() {
        UUID patternId = insertPattern("always-fail", "Always Fail");
        patternUsageService.recordUsage(buildRequest(patternId, "/proj/x", "fix_bug", false));
        patternUsageService.recordUsage(buildRequest(patternId, "/proj/y", "fix_bug", false));

        Double rate = patternUsageService.calculateSuccessRate(patternId);

        assertThat(rate).isNotNull();
        assertThat(rate).isCloseTo(0.0, within(0.01));
    }

    // ==================== Validation ====================

    @Test
    void shouldThrowWhenRequestIsNull() {
        assertThatThrownBy(() -> patternUsageService.recordUsage(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    void shouldThrowWhenPatternIdIsNull() {
        PatternUsageRequest request = PatternUsageRequest.builder()
                .projectPath("/test/proj")
                .taskType("fix_bug")
                .success(true)
                .build();

        assertThatThrownBy(() -> patternUsageService.recordUsage(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pattern ID is required");
    }

    @Test
    void shouldThrowWhenProjectPathIsNull() {
        PatternUsageRequest request = PatternUsageRequest.builder()
                .patternId(UUID.randomUUID())
                .taskType("fix_bug")
                .success(true)
                .build();

        assertThatThrownBy(() -> patternUsageService.recordUsage(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project path is required");
    }

    @Test
    void shouldThrowWhenProjectPathIsBlank() {
        PatternUsageRequest request = PatternUsageRequest.builder()
                .patternId(UUID.randomUUID())
                .projectPath("   ")
                .taskType("fix_bug")
                .success(true)
                .build();

        assertThatThrownBy(() -> patternUsageService.recordUsage(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project path is required");
    }

    @Test
    void shouldThrowWhenTaskTypeIsNull() {
        PatternUsageRequest request = PatternUsageRequest.builder()
                .patternId(UUID.randomUUID())
                .projectPath("/test/proj")
                .success(true)
                .build();

        assertThatThrownBy(() -> patternUsageService.recordUsage(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Task type is required");
    }

    @Test
    void shouldThrowWhenSuccessIsNull() {
        // success not set in builder → null Boolean
        PatternUsageRequest request = PatternUsageRequest.builder()
                .patternId(UUID.randomUUID())
                .projectPath("/test/proj")
                .taskType("fix_bug")
                .build();

        assertThatThrownBy(() -> patternUsageService.recordUsage(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Success flag is required");
    }

    @Test
    void shouldThrowWhenCodeQualityScoreIsOutOfRange() {
        UUID patternId = insertPattern("quality-pat", "Quality Pattern");
        PatternUsageRequest request = buildRequest(patternId, "/test/proj", "fix_bug", true);
        request.setCodeQualityScore(1.5);

        assertThatThrownBy(() -> patternUsageService.recordUsage(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Code quality score must be between 0.0 and 1.0");
    }

    @Test
    void shouldThrowWhenCodeQualityScoreIsNegative() {
        UUID patternId = insertPattern("quality-pat2", "Quality Pattern 2");
        PatternUsageRequest request = buildRequest(patternId, "/test/proj", "fix_bug", true);
        request.setCodeQualityScore(-0.1);

        assertThatThrownBy(() -> patternUsageService.recordUsage(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Code quality score must be between 0.0 and 1.0");
    }

    // ==================== Helpers ====================

    private UUID insertPattern(String name, String title) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(PATTERNS)
                .set(PATTERNS.PATTERN_ID, id)
                .set(PATTERNS.PATTERN_NAME, name)
                .set(PATTERNS.TITLE, title)
                .set(PATTERNS.DESCRIPTION, "Test description")
                .set(PATTERNS.IS_PROJECT_STANDARD, false)
                .set(PATTERNS.IS_GLOBAL_STANDARD, false)
                .set(PATTERNS.LANGUAGES, new String[]{"java"})
                .set(PATTERNS.FRAMEWORKS, new String[0])
                .set(PATTERNS.APPLIES_TO, new String[0])
                .set(PATTERNS.CREATED_AT, LocalDateTime.now())
                .execute();
        return id;
    }

    private PatternUsageRequest buildRequest(
            UUID patternId, String projectPath, String taskType, boolean success) {
        return PatternUsageRequest.builder()
                .patternId(patternId)
                .projectPath(projectPath)
                .taskType(taskType)
                .success(success)
                .build();
    }
}

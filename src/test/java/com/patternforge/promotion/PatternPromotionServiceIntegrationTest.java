package com.patternforge.promotion;

import com.patternforge.AbstractIntegrationTest;
import com.patternforge.jooq.tables.records.ConversationalPatternsRecord;
import com.patternforge.jooq.tables.records.PatternsRecord;
import com.patternforge.jooq.tables.records.ProjectsRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.patternforge.jooq.Tables.CONVERSATIONAL_PATTERNS;
import static com.patternforge.jooq.Tables.PATTERNS;
import static com.patternforge.jooq.Tables.PATTERN_USAGE;
import static com.patternforge.jooq.Tables.PROJECTS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for PatternPromotionService — full promotion pipeline.
 */
class PatternPromotionServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PatternPromotionService promotionService;

    // ==================== promoteToGlobal ====================

    @Test
    void shouldReturnFalseForNullPatternId() {
        boolean result = promotionService.promoteToGlobal(null);
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseForNonExistentPattern() {
        boolean result = promotionService.promoteToGlobal(UUID.randomUUID());
        assertThat(result).isFalse();
    }

    @Test
    void shouldNotPromoteToGlobalWhenUsedInFewerThanThreeProjects() {
        // Arrange
        UUID patternId = insertPattern("test-pattern", "Test Pattern");
        UUID projectId1 = insertProject("proj1", "/proj1");
        UUID projectId2 = insertProject("proj2", "/proj2");

        insertUsage(patternId, projectId1, true);
        insertUsage(patternId, projectId2, true);

        // Act
        boolean promoted = promotionService.promoteToGlobal(patternId);

        // Assert
        assertThat(promoted).isFalse();
        PatternsRecord pattern = dsl.selectFrom(PATTERNS)
                .where(PATTERNS.PATTERN_ID.eq(patternId))
                .fetchOne();
        assertThat(pattern).isNotNull();
        assertThat(pattern.getIsGlobalStandard()).isFalse();
    }

    @Test
    void shouldPromoteToGlobalWhenUsedInThreeDistinctProjects() {
        // Arrange
        UUID patternId = insertPattern("multi-project-pattern", "Multi Project Pattern");
        UUID projectId1 = insertProject("proj1", "/proj1");
        UUID projectId2 = insertProject("proj2", "/proj2");
        UUID projectId3 = insertProject("proj3", "/proj3");

        insertUsage(patternId, projectId1, true);
        insertUsage(patternId, projectId2, true);
        insertUsage(patternId, projectId3, true);

        // Act
        boolean promoted = promotionService.promoteToGlobal(patternId);

        // Assert
        assertThat(promoted).isTrue();
        PatternsRecord pattern = dsl.selectFrom(PATTERNS)
                .where(PATTERNS.PATTERN_ID.eq(patternId))
                .fetchOne();
        assertThat(pattern).isNotNull();
        assertThat(pattern.getIsGlobalStandard()).isTrue();
        assertThat(pattern.getGlobalSince()).isNotNull();
    }

    @Test
    void shouldCountDistinctProjectsNotTotalUsageRows() {
        // Arrange - same project used 5 times, should NOT trigger global promotion
        UUID patternId = insertPattern("single-project-pattern", "Single Project Pattern");
        UUID projectId = insertProject("only-proj", "/only");

        // 5 usage rows all from the same project
        for (int i = 0; i < 5; i++) {
            insertUsage(patternId, projectId, true);
        }

        // Act
        boolean promoted = promotionService.promoteToGlobal(patternId);

        // Assert - should NOT be promoted even though there are 5 usage records
        assertThat(promoted).isFalse();
        PatternsRecord pattern = dsl.selectFrom(PATTERNS)
                .where(PATTERNS.PATTERN_ID.eq(patternId))
                .fetchOne();
        assertThat(pattern).isNotNull();
        assertThat(pattern.getIsGlobalStandard()).isFalse();
    }

    // ==================== checkAndPromoteToGlobal ====================

    @Test
    void shouldPromoteAllEligibleProjectStandards() {
        // Arrange — two patterns eligible, one not
        UUID patternId1 = insertPattern("pattern-one", "Pattern One");
        UUID patternId2 = insertPattern("pattern-two", "Pattern Two");
        UUID patternId3 = insertPattern("pattern-three", "Pattern Three");

        // Mark all as project standards
        dsl.update(PATTERNS).set(PATTERNS.IS_PROJECT_STANDARD, true)
                .where(PATTERNS.PATTERN_ID.in(patternId1, patternId2, patternId3))
                .execute();

        // Give pattern1 and pattern2 usage in 3 distinct projects
        for (int i = 0; i < 3; i++) {
            UUID projId = insertProject("proj-a" + i, "/proj-a" + i);
            insertUsage(patternId1, projId, true);
            insertUsage(patternId2, projId, true);
        }

        // pattern3 only used in 2 projects
        UUID p1 = insertProject("proj-b0", "/proj-b0");
        UUID p2 = insertProject("proj-b1", "/proj-b1");
        insertUsage(patternId3, p1, true);
        insertUsage(patternId3, p2, true);

        // Act
        int count = promotionService.checkAndPromoteToGlobal();

        // Assert
        assertThat(count).isEqualTo(2);
        assertThat(isGlobal(patternId1)).isTrue();
        assertThat(isGlobal(patternId2)).isTrue();
        assertThat(isGlobal(patternId3)).isFalse();
    }

    @Test
    void shouldReturnZeroWhenNoEligiblePatterns() {
        // Arrange — pattern with only 2 projects
        UUID patternId = insertPattern("ineligible", "Ineligible Pattern");
        dsl.update(PATTERNS).set(PATTERNS.IS_PROJECT_STANDARD, true)
                .where(PATTERNS.PATTERN_ID.eq(patternId))
                .execute();
        UUID p1 = insertProject("x1", "/x1");
        UUID p2 = insertProject("x2", "/x2");
        insertUsage(patternId, p1, true);
        insertUsage(patternId, p2, true);

        int count = promotionService.checkAndPromoteToGlobal();

        assertThat(count).isEqualTo(0);
    }

    // ==================== checkAndPromotePatterns ====================

    @Test
    void shouldReturnZeroWhenNoConversationalPatternsAreEligible() {
        // Arrange — conversational pattern with promotion_count < 5
        UUID projectId = insertProject("low-count-proj", "/low");
        insertConversationalPattern("low count pattern", "user_explicit", projectId, 3, false);

        int count = promotionService.checkAndPromotePatterns();

        assertThat(count).isEqualTo(0);
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

    private UUID insertProject(String name, String path) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(PROJECTS)
                .set(PROJECTS.PROJECT_ID, id)
                .set(PROJECTS.PROJECT_NAME, name)
                .set(PROJECTS.PROJECT_PATH, path)
                .execute();
        return id;
    }

    private void insertUsage(UUID patternId, UUID projectId, boolean success) {
        dsl.insertInto(PATTERN_USAGE)
                .set(PATTERN_USAGE.PATTERN_ID, patternId)
                .set(PATTERN_USAGE.PROJECT_ID, projectId)
                .set(PATTERN_USAGE.TASK_TYPE, "test_task")
                .set(PATTERN_USAGE.SUCCESS, success)
                .set(PATTERN_USAGE.TIMESTAMP, LocalDateTime.now())
                .execute();
    }

    private void insertConversationalPattern(
            String description, String source, UUID projectId,
            int promotionCount, boolean isProjectStandard) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(CONVERSATIONAL_PATTERNS)
                .set(CONVERSATIONAL_PATTERNS.ID, id)
                .set(CONVERSATIONAL_PATTERNS.DESCRIPTION, description)
                .set(CONVERSATIONAL_PATTERNS.SOURCE, source)
                .set(CONVERSATIONAL_PATTERNS.PROJECT_ID, projectId)
                .set(CONVERSATIONAL_PATTERNS.PROMOTION_COUNT, promotionCount)
                .set(CONVERSATIONAL_PATTERNS.IS_PROJECT_STANDARD, isProjectStandard)
                .set(CONVERSATIONAL_PATTERNS.IS_GLOBAL_STANDARD, false)
                .set(CONVERSATIONAL_PATTERNS.CONFIDENCE, 0.95)
                .execute();
    }

    private boolean isGlobal(UUID patternId) {
        PatternsRecord record = dsl.selectFrom(PATTERNS)
                .where(PATTERNS.PATTERN_ID.eq(patternId))
                .fetchOne();
        return record != null && Boolean.TRUE.equals(record.getIsGlobalStandard());
    }
}

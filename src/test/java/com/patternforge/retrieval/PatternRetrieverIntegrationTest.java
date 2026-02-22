package com.patternforge.retrieval;

import com.patternforge.AbstractIntegrationTest;
import com.patternforge.retrieval.model.RetrievedPattern;
import com.patternforge.retrieval.model.TaskContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.patternforge.jooq.Tables.CONVERSATIONAL_PATTERNS;
import static com.patternforge.jooq.Tables.PATTERNS;
import static com.patternforge.jooq.Tables.PROJECTS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for PatternRetriever.
 * Verifies that global standards are always included regardless of topK,
 * deduplication works correctly, and conversational patterns are retrieved by session.
 */
class PatternRetrieverIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PatternRetriever patternRetriever;

    // ==================== Global Standards Always Included ====================

    @Test
    void shouldReturnEmptyListWhenNoPatternsExist() {
        TaskContext context = buildContext("general", "java");
        List<RetrievedPattern> results = patternRetriever.retrieve(context, 5, null, null);
        assertThat(results).isEmpty();
    }

    @Test
    void shouldReturnNullContextAsEmptyList() {
        List<RetrievedPattern> results = patternRetriever.retrieve(null, 5, null, null);
        assertThat(results).isEmpty();
    }

    @Test
    void shouldAlwaysIncludeGlobalStandardsRegardlessOfTopK() {
        // Arrange — 3 global standards
        insertPattern("global-1", "Global Standard One", true, false);
        insertPattern("global-2", "Global Standard Two", true, false);
        insertPattern("global-3", "Global Standard Three", true, false);

        // Also insert non-global patterns
        for (int i = 0; i < 10; i++) {
            insertPattern("local-" + i, "Local Pattern " + i, false, false);
        }

        TaskContext context = buildContext("general", "java");

        // Act — topK=1 should NOT limit global standards
        List<RetrievedPattern> results = patternRetriever.retrieve(context, 1, null, null);

        // Assert — all 3 global standards must be present
        long globalCount = results.stream()
                .filter(p -> p.getPatternData() != null
                        && "global_standard".equals(p.getPatternData().get("retrieval_reason")))
                .count();
        assertThat(globalCount).isEqualTo(3);
    }

    @Test
    void shouldDeduplicatePatternsAcrossSources() {
        // Arrange — global standard pattern that also appears in keyword search results
        UUID patternId = insertPattern("shared-pattern", "Shared Pattern", true, false);

        TaskContext context = buildContext("shared", "java");
        List<RetrievedPattern> results = patternRetriever.retrieve(context, 10, null, null);

        // Assert — pattern appears only once despite being in multiple sources
        long occurrences = results.stream()
                .filter(p -> patternId.toString().equals(p.getPatternId()))
                .count();
        assertThat(occurrences).isLessThanOrEqualTo(1);
    }

    @Test
    void shouldIncludeConversationalPatternsWhenConversationIdProvided() {
        // Arrange
        UUID projectId = insertProject("conv-project", "/conv/project");
        String conversationId = "test-conv-" + UUID.randomUUID();
        insertConversationalPattern("Use try-with-resources", "user_explicit", projectId, conversationId, 0, false);

        TaskContext context = buildContext("general", "java");

        // Act
        List<RetrievedPattern> results = patternRetriever.retrieve(context, 5, null, conversationId);

        // Assert — at least one conversational pattern present
        boolean hasConversational = results.stream()
                .anyMatch(p -> p.getPatternData() != null
                        && "session_pattern".equals(p.getPatternData().get("retrieval_reason")));
        assertThat(hasConversational).isTrue();
    }

    @Test
    void shouldNotIncludeConversationalPatternsWhenConversationIdIsNull() {
        // Arrange
        UUID projectId = insertProject("proj-no-conv", "/no/conv");
        insertConversationalPattern("Pattern without conv", "user_explicit", projectId, "some-conv-id", 0, false);

        TaskContext context = buildContext("general", "java");

        // Act — no conversationId provided
        List<RetrievedPattern> results = patternRetriever.retrieve(context, 5, null, null);

        // Assert — no session patterns
        boolean hasConversational = results.stream()
                .anyMatch(p -> p.getPatternData() != null
                        && "session_pattern".equals(p.getPatternData().get("retrieval_reason")));
        assertThat(hasConversational).isFalse();
    }

    @Test
    void shouldLegacyRetrieveWorkWithTwoArgs() {
        // Arrange — insert a global pattern
        insertPattern("legacy-global", "Legacy Global", true, false);

        TaskContext context = buildContext("general", "java");

        // Act — legacy 2-arg signature
        List<RetrievedPattern> results = patternRetriever.retrieve(context, 5);

        // Assert
        assertThat(results).isNotEmpty();
    }

    // ==================== Helpers ====================

    private UUID insertPattern(String name, String title, boolean isGlobal, boolean isProject) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(PATTERNS)
                .set(PATTERNS.PATTERN_ID, id)
                .set(PATTERNS.PATTERN_NAME, name)
                .set(PATTERNS.TITLE, title)
                .set(PATTERNS.DESCRIPTION, title + " description")
                .set(PATTERNS.IS_GLOBAL_STANDARD, isGlobal)
                .set(PATTERNS.IS_PROJECT_STANDARD, isProject)
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

    private void insertConversationalPattern(
            String description, String source, UUID projectId,
            String conversationId, int promotionCount, boolean isProjectStandard) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(CONVERSATIONAL_PATTERNS)
                .set(CONVERSATIONAL_PATTERNS.ID, id)
                .set(CONVERSATIONAL_PATTERNS.DESCRIPTION, description)
                .set(CONVERSATIONAL_PATTERNS.SOURCE, source)
                .set(CONVERSATIONAL_PATTERNS.PROJECT_ID, projectId)
                .set(CONVERSATIONAL_PATTERNS.CONVERSATION_ID, conversationId)
                .set(CONVERSATIONAL_PATTERNS.PROMOTION_COUNT, promotionCount)
                .set(CONVERSATIONAL_PATTERNS.IS_PROJECT_STANDARD, isProjectStandard)
                .set(CONVERSATIONAL_PATTERNS.IS_GLOBAL_STANDARD, false)
                .set(CONVERSATIONAL_PATTERNS.CONFIDENCE, 0.95)
                .execute();
    }

    private TaskContext buildContext(String taskType, String language) {
        return TaskContext.builder()
                .description("test task")
                .taskType(taskType)
                .language(language)
                .components(List.of())
                .concerns(List.of())
                .build();
    }
}

package com.patternforge.storage.repository;

import com.patternforge.AbstractIntegrationTest;
import com.patternforge.jooq.tables.records.PatternsRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.patternforge.jooq.Tables.PATTERNS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for PatternRepository — including new methods added for the promotion pipeline.
 */
class PatternRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PatternRepository patternRepository;

    // ==================== findAll ====================

    @Test
    void shouldReturnEmptyListWhenNoPatternsExist() {
        List<PatternsRecord> results = patternRepository.findAll();
        assertThat(results).isEmpty();
    }

    @Test
    void shouldReturnAllPatterns() {
        insertPattern("pattern-one", "Pattern One", false, false);
        insertPattern("pattern-two", "Pattern Two", false, false);

        List<PatternsRecord> results = patternRepository.findAll();

        assertThat(results).hasSize(2);
    }

    // ==================== findById ====================

    @Test
    void shouldReturnEmptyOptionalForNonExistentId() {
        Optional<PatternsRecord> result = patternRepository.findById(UUID.randomUUID());
        assertThat(result).isEmpty();
    }

    @Test
    void shouldFindPatternById() {
        UUID id = insertPattern("find-me", "Find Me", false, false);

        Optional<PatternsRecord> result = patternRepository.findById(id);

        assertThat(result).isPresent();
        assertThat(result.get().getPatternName()).isEqualTo("find-me");
        assertThat(result.get().getTitle()).isEqualTo("Find Me");
    }

    // ==================== findGlobalPatterns ====================

    @Test
    void shouldReturnOnlyGlobalPatterns() {
        insertPattern("global-one", "Global One", true, true);
        insertPattern("global-two", "Global Two", true, true);
        insertPattern("local-one", "Local One", false, false);
        insertPattern("local-two", "Local Two", false, false);

        List<PatternsRecord> globals = patternRepository.findGlobalPatterns();

        assertThat(globals).hasSize(2);
        assertThat(globals).allMatch(PatternsRecord::getIsGlobalStandard);
    }

    @Test
    void shouldReturnEmptyWhenNoGlobalPatterns() {
        insertPattern("non-global", "Non Global", false, false);

        List<PatternsRecord> globals = patternRepository.findGlobalPatterns();

        assertThat(globals).isEmpty();
    }

    // ==================== findProjectStandardsNotGlobal ====================

    @Test
    void shouldReturnProjectStandardsThatAreNotGlobal() {
        insertPattern("project-only", "Project Only", false, true);  // project=true, global=false
        insertPattern("both-flags", "Both Flags", true, true);       // project=true, global=true
        insertPattern("neither", "Neither", false, false);            // project=false, global=false

        List<PatternsRecord> results = patternRepository.findProjectStandardsNotGlobal();

        assertThat(results).hasSize(1);
        PatternsRecord record = results.get(0);
        assertThat(record.getPatternName()).isEqualTo("project-only");
        assertThat(record.getIsProjectStandard()).isTrue();
        assertThat(record.getIsGlobalStandard()).isFalse();
    }

    @Test
    void shouldReturnEmptyWhenNoProjectStandardsExist() {
        insertPattern("global-only", "Global Only", true, false);  // is_global=true but is_project=false
        insertPattern("neither", "Neither", false, false);

        List<PatternsRecord> results = patternRepository.findProjectStandardsNotGlobal();

        assertThat(results).isEmpty();
    }

    @Test
    void shouldReturnMultipleProjectStandardsNotGlobal() {
        insertPattern("proj-std-1", "Proj Std 1", false, true);
        insertPattern("proj-std-2", "Proj Std 2", false, true);
        insertPattern("global-std", "Global Std", true, true);   // excluded
        insertPattern("regular", "Regular", false, false);         // excluded

        List<PatternsRecord> results = patternRepository.findProjectStandardsNotGlobal();

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> Boolean.TRUE.equals(r.getIsProjectStandard()));
        assertThat(results).noneMatch(PatternsRecord::getIsGlobalStandard);
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
}

package com.patternforge.util;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Utility class for cleaning up database state between integration tests.
 * 
 * <p>Performs two cleanup operations:
 * <ol>
 *   <li>Truncates all application tables in dependency order (CASCADE)</li>
 *   <li>Resets all sequences to start from 1</li>
 * </ol>
 * 
 * <p>This ensures complete test isolation and prevents test interdependencies.
 * 
 * @see com.patternforge.AbstractIntegrationTest
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CleanUpUtil {

    private final DSLContext dsl;

    /**
     * Tables to truncate in order (CASCADE handles dependencies automatically).
     */
    private static final List<String> TABLES = List.of(
            "pattern_usage",
            "pattern_promotions",
            "pattern_quality_gates",
            "conversational_patterns",
            "patterns",
            "projects",
            "workflow_steps",
            "workflow_templates",
            "quality_gates",
            "rules"
    );

    /**
     * Cleans the entire database by truncating all tables and resetting sequences.
     * 
     * <p>This method should be called in {@code @AfterEach} to ensure test isolation.
     * 
     * @throws RuntimeException if database cleanup fails (wrapped SQLException)
     */
    @SneakyThrows
    public void cleanupDatabase() {
        if (Objects.isNull(dsl)) {
            log.warn("DSLContext is null, skipping database cleanup");
            return;
        }

        log.debug("Starting database cleanup");

        // Step 1: Truncate all tables
        for (String table : TABLES) {
            try {
                dsl.execute("TRUNCATE TABLE " + table + " RESTART IDENTITY CASCADE");
                log.trace("Truncated table: {}", table);
            } catch (Exception exception) {
                log.warn("Failed to truncate table {}: {}", table, exception.getMessage());
            }
        }

        // Step 2: Reset all sequences to start from 1
        Set<String> sequences = new HashSet<>();
        List<Record> sequenceRecords = dsl.fetch(
                "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = 'public'"
        );

        for (Record record : sequenceRecords) {
            sequences.add(record.get(0, String.class));
        }

        for (String sequence : sequences) {
            try {
                dsl.execute("ALTER SEQUENCE " + sequence + " RESTART WITH 1");
                log.trace("Reset sequence: {}", sequence);
            } catch (Exception exception) {
                log.warn("Failed to reset sequence {}: {}", sequence, exception.getMessage());
            }
        }

        log.debug("Database cleanup completed: {} tables truncated, {} sequences reset",
                TABLES.size(), sequences.size());
    }
}

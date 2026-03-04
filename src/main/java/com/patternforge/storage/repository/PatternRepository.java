package com.patternforge.storage.repository;

import com.patternforge.jooq.tables.records.PatternsRecord;
import com.patternforge.util.UuidSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.patternforge.jooq.Tables.PATTERNS;

/**
 * Repository for pattern persistence operations using jOOQ.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class PatternRepository {
    
    private final DSLContext dsl;
    
    public List<PatternsRecord> findAll() {
        return dsl.selectFrom(PATTERNS)
            .fetch();
    }
    
    public Optional<PatternsRecord> findById(UUID id) {
        if (Objects.isNull(id)) {
            return Optional.empty();
        }
        
        PatternsRecord record = dsl.selectFrom(PATTERNS)
            .where(PATTERNS.PATTERN_ID.eq(id))
            .fetchOne();
        
        return Optional.ofNullable(record);
    }
    
    public Optional<PatternsRecord> findByName(String name) {
        if (Objects.isNull(name) || name.isBlank()) {
            return Optional.empty();
        }
        
        PatternsRecord record = dsl.selectFrom(PATTERNS)
            .where(PATTERNS.PATTERN_NAME.eq(name))
            .fetchOne();
        
        return Optional.ofNullable(record);
    }
    
    public List<PatternsRecord> findByLanguage(String language) {
        if (Objects.isNull(language) || language.isBlank()) {
            return List.of();
        }
        
        return dsl.selectFrom(PATTERNS)
            .where(DSL.condition("{0} = ANY(languages)", language))
            .fetch();
    }
    
    public List<PatternsRecord> findGlobalPatterns() {
        return dsl.selectFrom(PATTERNS)
            .where(PATTERNS.IS_GLOBAL_STANDARD.isTrue())
            .fetch();
    }

    /**
     * Returns the top {@code limit} global standard patterns ordered by success rate descending.
     * Use this in query paths to avoid returning every global standard on every request.
     */
    public List<PatternsRecord> findGlobalPatterns(int limit) {
        return dsl.selectFrom(PATTERNS)
            .where(PATTERNS.IS_GLOBAL_STANDARD.isTrue())
            .orderBy(PATTERNS.SUCCESS_RATE.desc().nullsLast())
            .limit(limit)
            .fetch();
    }

    /**
     * Returns up to {@code limit} project-standard patterns whose source path references
     * {@code projectPath}, ordered by success rate descending.
     */
    public List<PatternsRecord> findProjectPatterns(String projectPath, int limit) {
        if (Objects.isNull(projectPath) || projectPath.isBlank()) {
            return List.of();
        }
        return dsl.selectFrom(PATTERNS)
            .where(PATTERNS.IS_PROJECT_STANDARD.isTrue())
            .and(PATTERNS.IS_GLOBAL_STANDARD.isFalse())
            .and(PATTERNS.SOURCE.like("%" + projectPath + "%"))
            .orderBy(PATTERNS.SUCCESS_RATE.desc().nullsLast())
            .limit(limit)
            .fetch();
    }

    public List<PatternsRecord> findProjectStandardsNotGlobal() {
        return dsl.selectFrom(PATTERNS)
            .where(PATTERNS.IS_PROJECT_STANDARD.isTrue())
            .and(PATTERNS.IS_GLOBAL_STANDARD.isFalse())
            .fetch();
    }
    
    public PatternsRecord save(PatternsRecord record) {
        if (Objects.nonNull(record.getPatternId())) {
            // Update existing record using DSLContext
            dsl.update(PATTERNS)
                .set(record)
                .where(PATTERNS.PATTERN_ID.eq(record.getPatternId()))
                .execute();
        } else {
            // Insert new record using DSLContext
            record.setPatternId(UuidSupplier.getInstance().get());
            dsl.insertInto(PATTERNS)
                .set(record)
                .execute();
        }
        return record;
    }
    
    /**
     * Upserts a list of pattern records.
     * For each record, if a pattern with the same pattern_name already exists its ID is reused
     * so that {@link #save} performs an UPDATE instead of an INSERT.
     *
     * @param records list of patterns to upsert
     * @return list of saved records (with IDs populated)
     */
    public List<PatternsRecord> saveAll(List<PatternsRecord> records) {
        if (Objects.isNull(records) || records.isEmpty()) {
            return List.of();
        }

        List<PatternsRecord> result = new ArrayList<>();
        for (PatternsRecord record : records) {
            Optional<PatternsRecord> existing = findByName(record.getPatternName());
            existing.ifPresent(existingRecord -> record.setPatternId(existingRecord.getPatternId()));
            result.add(save(record));
        }

        log.info("saveAll: {} patterns upserted", result.size());
        return result;
    }

    public void updateEmbedding(UUID patternId, float[] embedding) {
        if (Objects.isNull(patternId) || Objects.isNull(embedding)) {
            return;
        }
        
        // Convert float[] to PostgreSQL vector format: [0.1, 0.2, 0.3]
        StringBuilder vectorStr = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                vectorStr.append(",");
            }
            vectorStr.append(embedding[i]);
        }
        vectorStr.append("]");
        
        dsl.execute("UPDATE patterns SET embedding = ?::vector WHERE pattern_id = ?",
            vectorStr.toString(), patternId);
        
        log.debug("Updated embedding for pattern: {}", patternId);
    }
}

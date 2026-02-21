package com.patternforge.storage.repository;

import com.patternforge.jooq.tables.records.ConversationalPatternsRecord;
import com.patternforge.util.UuidSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.patternforge.jooq.Tables.CONVERSATIONAL_PATTERNS;

/**
 * Repository for conversational pattern persistence operations using jOOQ.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class ConversationalPatternRepository {
    
    private final DSLContext dsl;
    
    public List<ConversationalPatternsRecord> findAll() {
        return dsl.selectFrom(CONVERSATIONAL_PATTERNS)
            .fetch();
    }
    
    public List<ConversationalPatternsRecord> findByProjectId(UUID projectId) {
        if (Objects.isNull(projectId)) {
            return List.of();
        }
        
        return dsl.selectFrom(CONVERSATIONAL_PATTERNS)
            .where(CONVERSATIONAL_PATTERNS.PROJECT_ID.eq(projectId))
            .fetch();
    }
    
    public List<ConversationalPatternsRecord> findByConversationId(String conversationId) {
        if (Objects.isNull(conversationId) || conversationId.isBlank()) {
            return List.of();
        }
        
        return dsl.selectFrom(CONVERSATIONAL_PATTERNS)
            .where(CONVERSATIONAL_PATTERNS.CONVERSATION_ID.eq(conversationId))
            .fetch();
    }
    
    public List<ConversationalPatternsRecord> findPendingPromotion() {
        return dsl.selectFrom(CONVERSATIONAL_PATTERNS)
            .where(CONVERSATIONAL_PATTERNS.PROMOTION_COUNT.greaterOrEqual(5))
            .and(CONVERSATIONAL_PATTERNS.IS_PROJECT_STANDARD.isFalse())
            .fetch();
    }
    
    public ConversationalPatternsRecord save(ConversationalPatternsRecord record) {
        if (Objects.nonNull(record.getId())) {
            // Update existing record using DSLContext
            dsl.update(CONVERSATIONAL_PATTERNS)
                .set(record)
                .where(CONVERSATIONAL_PATTERNS.ID.eq(record.getId()))
                .execute();
        } else {
            // Insert new record using DSLContext
            record.setId(UuidSupplier.getInstance().get());
            dsl.insertInto(CONVERSATIONAL_PATTERNS)
                .set(record)
                .execute();
        }
        return record;
    }
    
    public void incrementPromotionCount(UUID id) {
        if (Objects.isNull(id)) {
            return;
        }
        
        dsl.update(CONVERSATIONAL_PATTERNS)
            .set(CONVERSATIONAL_PATTERNS.PROMOTION_COUNT, 
                CONVERSATIONAL_PATTERNS.PROMOTION_COUNT.plus(1))
            .where(CONVERSATIONAL_PATTERNS.ID.eq(id))
            .execute();
        
        log.debug("Incremented promotion count for pattern: {}", id);
    }
    
    public void promoteToProjectStandard(UUID id) {
        if (Objects.isNull(id)) {
            return;
        }
        
        dsl.update(CONVERSATIONAL_PATTERNS)
            .set(CONVERSATIONAL_PATTERNS.IS_PROJECT_STANDARD, true)
            .where(CONVERSATIONAL_PATTERNS.ID.eq(id))
            .execute();
        
        log.info("Promoted pattern to project standard: {}", id);
    }
}

package com.patternforge.promotion;

import com.patternforge.extraction.EmbeddingService;
import com.patternforge.jooq.tables.records.ConversationalPatternsRecord;
import com.patternforge.jooq.tables.records.PatternsRecord;
import com.patternforge.storage.repository.ConversationalPatternRepository;
import com.patternforge.storage.repository.PatternRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.patternforge.jooq.Tables.PATTERN_PROMOTIONS;
import static com.patternforge.jooq.Tables.PATTERN_USAGE;

/**
 * Service for promoting patterns from conversational to project standard
 * and from project standard to global standard.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PatternPromotionService {
    
    private final ConversationalPatternRepository conversationalPatternRepository;
    private final PatternRepository patternRepository;
    private final EmbeddingService embeddingService;
    private final DSLContext dsl;
    
    /**
     * Runs the full promotion pipeline in one call:
     * <ol>
     *   <li>Conversational patterns with promotion_count &ge; 5 → project standard</li>
     *   <li>Immediately tries global promotion for each newly promoted pattern</li>
     *   <li>Scans all existing project standards and promotes any that now qualify as global</li>
     * </ol>
     *
     * @return total count of promotions across both levels
     */
    @Transactional
    public int checkAndPromotePatterns() {
        List<ConversationalPatternsRecord> candidates =
            conversationalPatternRepository.findPendingPromotion();

        int toProjectCount = 0;
        int toGlobalCount = 0;

        for (ConversationalPatternsRecord conversational : candidates) {
            try {
                UUID formalPatternId = conversational.getFormalPatternId();

                // Create formal pattern if it doesn't exist yet
                if (Objects.isNull(formalPatternId)) {
                    formalPatternId = createFormalPatternFromConversational(conversational);
                    conversational.setFormalPatternId(formalPatternId);
                    conversational.store();
                }

                // Promote conversational → project standard
                conversationalPatternRepository.promoteToProjectStandard(conversational.getId());
                logPromotion(formalPatternId, "system", "conversational", "project", "automatic", false);
                toProjectCount++;
                log.info("Promoted pattern '{}' to project standard", conversational.getDescription());

                // Immediately try global promotion — this pattern may already be used in 3+ projects
                if (promoteToGlobal(formalPatternId)) {
                    toGlobalCount++;
                }

            } catch (Exception exception) {
                log.error("Failed to promote pattern {}: {}",
                    conversational.getId(), exception.getMessage(), exception);
            }
        }

        // Also scan all existing project standards — any that crossed the 3-project threshold
        toGlobalCount += checkAndPromoteToGlobal();

        log.info("Promotion run complete: {} conversational→project, {} project→global",
            toProjectCount, toGlobalCount);
        return toProjectCount + toGlobalCount;
    }

    /**
     * Scans all project standards not yet marked global and promotes any that are
     * used in 3 or more distinct projects.
     *
     * @return count of patterns promoted to global standard
     */
    @Transactional
    public int checkAndPromoteToGlobal() {
        List<PatternsRecord> candidates = patternRepository.findProjectStandardsNotGlobal();

        int promotedCount = 0;
        for (PatternsRecord pattern : candidates) {
            if (promoteToGlobal(pattern.getPatternId())) {
                promotedCount++;
            }
        }

        if (promotedCount > 0) {
            log.info("Promoted {} project standards to global standard", promotedCount);
        }
        return promotedCount;
    }
    
    /**
     * Promote a project pattern to global standard.
     *
     * @param patternId the pattern to promote
     * @return true if promotion successful, false otherwise
     */
    @Transactional
    public boolean promoteToGlobal(UUID patternId) {
        if (Objects.isNull(patternId)) {
            log.warn("Cannot promote null pattern ID to global");
            return false;
        }
        
        PatternsRecord pattern = patternRepository.findById(patternId).orElse(null);
        
        if (Objects.isNull(pattern)) {
            log.warn("Pattern not found: {}", patternId);
            return false;
        }
        
        // Check if pattern is used in 3+ distinct projects (not just 3 usage records)
        Integer distinctProjectCount = dsl.select(DSL.countDistinct(PATTERN_USAGE.PROJECT_ID))
            .from(PATTERN_USAGE)
            .where(PATTERN_USAGE.PATTERN_ID.eq(patternId))
            .fetchOne(0, Integer.class);

        if (Objects.isNull(distinctProjectCount) || distinctProjectCount < 3) {
            log.debug("Pattern {} used in only {} distinct project(s), need 3+ for global promotion",
                patternId, distinctProjectCount);
            return false;
        }
        
        try {
            // Promote to global standard
            pattern.setIsGlobalStandard(true);
            pattern.setGlobalSince(LocalDate.now());
            pattern.store();
            
            // Log promotion
            logPromotion(
                patternId,
                "system",
                "project",
                "global",
                "automatic",
                false
            );
            
            log.info("Promoted pattern '{}' to global standard", pattern.getPatternName());
            return true;
            
        } catch (Exception exception) {
            log.error("Failed to promote pattern {} to global: {}", 
                patternId, exception.getMessage(), exception);
            return false;
        }
    }
    
    /**
     * Create a formal pattern from a conversational pattern.
     *
     * @param conversational the conversational pattern
     * @return the created pattern ID
     */
    @Transactional
    public UUID createFormalPatternFromConversational(ConversationalPatternsRecord conversational) {
        if (Objects.isNull(conversational)) {
            throw new IllegalArgumentException("Conversational pattern cannot be null");
        }
        
        PatternsRecord pattern = dsl.newRecord(com.patternforge.jooq.tables.Patterns.PATTERNS);
        
        // Set basic fields
        pattern.setPatternId(UUID.randomUUID());
        pattern.setPatternName(generatePatternName(conversational));
        pattern.setTitle(conversational.getDescription());
        pattern.setDescription(conversational.getDescription());
        pattern.setCategory("conversational");
        pattern.setScope("project");
        
        // Set project standard flags
        pattern.setIsProjectStandard(true);
        pattern.setIsGlobalStandard(false);
        
        // Copy metadata from conversational pattern
        pattern.setSource(conversational.getSource());
        pattern.setConfidence(conversational.getConfidence());
        pattern.setCreatedAt(LocalDateTime.now());
        
        // Create code examples JSON from conversational pattern
        if (Objects.nonNull(conversational.getCodeExample())) {
            String codeExamplesJson = String.format(
                "{\"default\": \"%s\"}", 
                escapeJson(conversational.getCodeExample())
            );
            pattern.setCodeExamples(JSONB.valueOf(codeExamplesJson));
        }
        
        // Set when_to_use from rationale
        if (Objects.nonNull(conversational.getRationale())) {
            pattern.setWhenToUse(conversational.getRationale());
        }
        
        // Initialize arrays
        pattern.setLanguages(new String[0]);
        pattern.setFrameworks(new String[0]);
        pattern.setAppliesTo(new String[0]);
        
        // Save pattern
        pattern.insert();
        
        // Generate and save embedding
        try {
            String embeddingText = String.format("%s %s %s",
                conversational.getDescription(),
                Objects.nonNull(conversational.getRationale()) ? conversational.getRationale() : "",
                Objects.nonNull(conversational.getCodeExample()) ? conversational.getCodeExample() : ""
            );
            
            float[] embedding = embeddingService.generateDocumentEmbedding(embeddingText);
            if (Objects.nonNull(embedding)) {
                patternRepository.updateEmbedding(pattern.getPatternId(), embedding);
            }
        } catch (Exception exception) {
            log.warn("Failed to generate embedding for pattern {}: {}", 
                pattern.getPatternId(), exception.getMessage());
        }
        
        log.info("Created formal pattern '{}' from conversational pattern", 
            pattern.getPatternName());
        
        return pattern.getPatternId();
    }
    
    /**
     * Log a promotion event to pattern_promotions table.
     */
    private void logPromotion(
        UUID patternId,
        String promotedBy,
        String fromLevel,
        String toLevel,
        String promotionType,
        boolean userPrompted
    ) {
        dsl.insertInto(PATTERN_PROMOTIONS)
            .set(PATTERN_PROMOTIONS.PATTERN_ID, patternId)
            .set(PATTERN_PROMOTIONS.PROMOTED_BY, promotedBy)
            .set(PATTERN_PROMOTIONS.FROM_LEVEL, fromLevel)
            .set(PATTERN_PROMOTIONS.TO_LEVEL, toLevel)
            .set(PATTERN_PROMOTIONS.PROMOTION_TYPE, promotionType)
            .set(PATTERN_PROMOTIONS.USER_PROMPTED, userPrompted)
            .execute();
        
        log.debug("Logged promotion: {} -> {} for pattern {}", fromLevel, toLevel, patternId);
    }
    
    /**
     * Generate a pattern name from conversational pattern description.
     * Converts description to kebab-case format.
     */
    private String generatePatternName(ConversationalPatternsRecord conversational) {
        String description = conversational.getDescription();
        
        if (Objects.isNull(description) || description.isBlank()) {
            return "pattern-" + UUID.randomUUID().toString().substring(0, 8);
        }
        
        // Convert to lowercase and replace non-alphanumeric with hyphens
        String patternName = description.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
        
        // Limit length
        if (patternName.length() > 100) {
            patternName = patternName.substring(0, 100);
        }
        
        return patternName;
    }
    
    /**
     * Escape JSON special characters.
     */
    private String escapeJson(String text) {
        if (Objects.isNull(text)) {
            return "";
        }
        
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}

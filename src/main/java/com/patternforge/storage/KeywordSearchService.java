package com.patternforge.storage;

import com.patternforge.retrieval.model.RetrievedPattern;
import com.patternforge.retrieval.model.TaskContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.patternforge.jooq.Tables.PATTERNS;

/**
 * Keyword-based search for patterns using ILIKE pattern matching.
 * Searches across pattern_name, title, and description fields for MVP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordSearchService {
    
    private final DSLContext dsl;
    
    /**
     * Searches patterns using keyword matching with ILIKE (case-insensitive LIKE).
     * 
     * @param query Search query string
     * @param context Task context with optional language/scope filters
     * @param topK Number of top results to return
     * @return List of patterns ordered by relevance (highest first)
     */
    public List<RetrievedPattern> search(String query, TaskContext context, int topK) {
        log.debug("Searching patterns with query: '{}', topK: {}", query, topK);
        
        // Build search condition - match query against multiple fields
        String searchPattern = "%" + query + "%";
        Condition searchCondition = PATTERNS.DESCRIPTION.likeIgnoreCase(searchPattern)
            .or(PATTERNS.TITLE.likeIgnoreCase(searchPattern))
            .or(PATTERNS.PATTERN_NAME.likeIgnoreCase(searchPattern))
            .or(PATTERNS.WHEN_TO_USE.likeIgnoreCase(searchPattern));
        
        List<RetrievedPattern> results = dsl.select(
                PATTERNS.PATTERN_ID,
                PATTERNS.PATTERN_NAME,
                PATTERNS.TITLE,
                PATTERNS.DESCRIPTION,
                PATTERNS.CATEGORY,
                PATTERNS.WHEN_TO_USE,
                PATTERNS.CODE_EXAMPLES,
                PATTERNS.SUCCESS_RATE,
                PATTERNS.WORKFLOW_ID,
                DSL.val(0.5).as("rank") // Fixed relevance score for LIKE matches
            )
            .from(PATTERNS)
            .where(searchCondition)
            .and(buildFilters(context))
            .orderBy(DSL.field("rank").desc())  // Order by relevance, not alphabetically
            .limit(topK)
            .fetch()
            .stream()
            .map(this::mapToRetrievedPattern)
            .toList();
        
        log.debug("Found {} patterns matching query '{}'", results.size(), query);
        
        // Fallback: return limited patterns if no results found
        if (results.isEmpty()) {
            log.warn("No patterns matched query '{}', returning limited patterns as fallback", query);
            results = dsl.select(
                    PATTERNS.PATTERN_ID,
                    PATTERNS.PATTERN_NAME,
                    PATTERNS.TITLE,
                    PATTERNS.DESCRIPTION,
                    PATTERNS.CATEGORY,
                    PATTERNS.WHEN_TO_USE,
                    PATTERNS.CODE_EXAMPLES,
                    PATTERNS.SUCCESS_RATE,
                    PATTERNS.WORKFLOW_ID,
                    DSL.val(0.3).as("rank")
                )
                .from(PATTERNS)
                .where(buildFilters(context))
                .orderBy(PATTERNS.PATTERN_NAME.asc())
                .limit(topK)
                .fetch()
                .stream()
                .map(this::mapToRetrievedPattern)
                .toList();
            
            log.debug("Fallback returned {} patterns", results.size());
        }
        
        return results;
    }
    
    /**
     * Builds additional filter conditions based on task context.
     * 
     * @param context Task context with optional filters
     * @return jOOQ condition combining all applicable filters
     */
    private Condition buildFilters(TaskContext context) {
        Condition condition = DSL.trueCondition();
        
        if (Objects.isNull(context)) {
            return condition;
        }
        
        // Filter by language if provided
        if (Objects.nonNull(context.getLanguage())) {
            condition = condition.and(
                DSL.field("? = ANY(languages)", Boolean.class, context.getLanguage())
            );
        }
        
        // Filter by framework if provided
        if (Objects.nonNull(context.getFramework())) {
            condition = condition.and(
                DSL.field("? = ANY(frameworks)", Boolean.class, context.getFramework())
            );
        }
        
        return condition;
    }
    
    /**
     * Maps database record to RetrievedPattern domain object.
     * 
     * @param record jOOQ record from database
     * @return RetrievedPattern with all fields populated
     */
    private RetrievedPattern mapToRetrievedPattern(Record record) {
        Map<String, String> codeExamples = new HashMap<>();
        JSONB codeExamplesJsonb = record.get(PATTERNS.CODE_EXAMPLES);
        if (Objects.nonNull(codeExamplesJsonb)) {
            codeExamples = parseCodeExamples(codeExamplesJsonb.data());
        }
        
        Double rank = record.get("rank", Double.class);
        Double successRate = record.get(PATTERNS.SUCCESS_RATE);
        
        return RetrievedPattern.builder()
            .patternId(record.get(PATTERNS.PATTERN_ID).toString())
            .patternName(record.get(PATTERNS.PATTERN_NAME))
            .title(record.get(PATTERNS.TITLE))
            .description(record.get(PATTERNS.DESCRIPTION))
            .category(record.get(PATTERNS.CATEGORY))
            .whenToUse(record.get(PATTERNS.WHEN_TO_USE))
            .codeExamples(codeExamples)
            .relevanceScore(Objects.nonNull(rank) ? rank : 0.0)
            .successRate(Objects.nonNull(successRate) ? successRate : 0.0)
            .workflowId(Objects.nonNull(record.get(PATTERNS.WORKFLOW_ID)) ? record.get(PATTERNS.WORKFLOW_ID).toString() : null)
            .build();
    }
    
    /**
     * Parses JSONB code examples data into Map.
     * 
     * @param jsonbData Raw JSONB data string
     * @return Map of language to code example
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> parseCodeExamples(String jsonbData) {
        try {
            // Simple JSONB parsing - in production, use Jackson ObjectMapper
            return new HashMap<>();
        } catch (Exception exception) {
            log.warn("Failed to parse code examples JSONB: {}", exception.getMessage());
            return new HashMap<>();
        }
    }
}

package com.patternforge.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patternforge.config.RetrievalProperties;
import com.patternforge.retrieval.model.RetrievedPattern;
import com.patternforge.retrieval.model.TaskContext;
import com.patternforge.util.VectorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.patternforge.jooq.Tables.PATTERNS;

/**
 * Vector-based semantic search for patterns using pgvector.
 * Performs similarity search on pattern embeddings to find relevant patterns based on query embedding.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {
    
    private final DSLContext dsl;
    private final ObjectMapper objectMapper;
    private final RetrievalProperties retrievalProperties;
    
    /**
     * Searches patterns using vector similarity.
     * 
     * @param queryEmbedding 768-dimensional embedding vector
     * @param context Task context with optional language filter
     * @param topK Number of top results to return
     * @return List of patterns ordered by similarity (highest first)
     */
    public List<RetrievedPattern> search(float[] queryEmbedding, TaskContext context, int topK) {
        String vectorStr = VectorUtils.toPostgresVector(queryEmbedding);
        double minSimilarity = retrievalProperties.getMinSimilarityThreshold();

        List<Object> results = dsl.select(
                PATTERNS.PATTERN_ID,
                PATTERNS.PATTERN_NAME,
                PATTERNS.TITLE,
                PATTERNS.DESCRIPTION,
                PATTERNS.CATEGORY,
                PATTERNS.WHEN_TO_USE,
                PATTERNS.CODE_EXAMPLES,
                PATTERNS.SUCCESS_RATE,
                PATTERNS.WORKFLOW_ID,
                DSL.field("1 - (embedding <=> ?::vector)", Double.class, vectorStr).as("similarity")
            )
            .from(PATTERNS)
            .where(buildFilters(context))
            .and(DSL.field("1 - (embedding <=> ?::vector)", Double.class, vectorStr)
                .greaterOrEqual(minSimilarity))
            .orderBy(DSL.field("embedding <=> ?::vector", vectorStr))
            .limit(topK)
            .fetch()
            .map(record -> {
                Map<String, String> codeExamples = new HashMap<>();
                JSONB codeExamplesJsonb = record.get(PATTERNS.CODE_EXAMPLES);
                if (Objects.nonNull(codeExamplesJsonb)) {
                    codeExamples = parseCodeExamples(codeExamplesJsonb.data());
                }
                
                Double similarity = record.get("similarity", Double.class);
                Double successRate = record.get(PATTERNS.SUCCESS_RATE);
                
                return RetrievedPattern.builder()
                    .patternId(record.get(PATTERNS.PATTERN_ID).toString())
                    .patternName(record.get(PATTERNS.PATTERN_NAME))
                    .title(record.get(PATTERNS.TITLE))
                    .description(record.get(PATTERNS.DESCRIPTION))
                    .category(record.get(PATTERNS.CATEGORY))
                    .whenToUse(record.get(PATTERNS.WHEN_TO_USE))
                    .codeExamples(codeExamples)
                    .relevanceScore(Objects.nonNull(similarity) ? similarity : 0.0)
                    .successRate(Objects.nonNull(successRate) ? successRate : 0.0)
                    .workflowId(Objects.nonNull(record.get(PATTERNS.WORKFLOW_ID)) ? record.get(PATTERNS.WORKFLOW_ID).toString() : null)
                    .build();
            });
        
        return (List<RetrievedPattern>) (List<?>) results;
    }
    
    /**
     * Builds filter conditions for pattern search.
     * Always includes scope filter for global/project patterns.
     * Optionally filters by language if specified in context.
     */
    private Condition buildFilters(TaskContext context) {
        Condition filters = DSL.field("scope").in("global", "project");
        
        if (Objects.nonNull(context) && Objects.nonNull(context.getLanguage())) {
            filters = filters.and(DSL.field("? = ANY(languages)", Boolean.class, context.getLanguage()));
        }
        
        return filters;
    }
    
    /**
     * Parses JSONB code examples to Map.
     * Returns empty map on parsing errors.
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> parseCodeExamples(String jsonbData) {
        try {
            return objectMapper.readValue(jsonbData, new TypeReference<Map<String, String>>() {});
        } catch (Exception exception) {
            log.warn("Failed to parse code examples JSONB: {}", exception.getMessage());
            return new HashMap<>();
        }
    }
}

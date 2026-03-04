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
     * Searches patterns using vector similarity (no threshold — returns all top-K).
     * Backward-compatible overload.
     */
    public List<RetrievedPattern> search(float[] queryEmbedding, TaskContext context, int topK) {
        return search(queryEmbedding, context, topK, 0.0);
    }

    /**
     * Searches patterns using vector similarity with a minimum similarity threshold.
     * Patterns below the threshold are dropped even if they fall within top-K.
     *
     * @param queryEmbedding      768-dimensional embedding vector
     * @param context             Task context with optional language filter
     * @param topK                Number of top results to return
     * @param similarityThreshold Minimum similarity score (0.0–1.0); patterns below this are excluded
     * @return List of patterns ordered by similarity (highest first), filtered by threshold
     */
    public List<RetrievedPattern> search(float[] queryEmbedding, TaskContext context, int topK, double similarityThreshold) {
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

        List<RetrievedPattern> typed = (List<RetrievedPattern>) (List<?>) results;

        if (similarityThreshold > 0.0) {
            int beforeCount = typed.size();
            typed = typed.stream()
                    .filter(p -> p.getRelevanceScore() >= similarityThreshold)
                    .toList();
            log.debug("Similarity threshold {} filtered {} → {} patterns", similarityThreshold, beforeCount, typed.size());
        }

        return typed;
    }
    
    /**
     * Searches ONLY global standard patterns by task similarity.
     *
     * <p>DCP-inspired: instead of always returning the top-N globals by success_rate
     * regardless of relevance, this ranks them by (similarity * success_rate) so the
     * globals most relevant to the current task surface first. A global standard that
     * is irrelevant to this task simply scores low and doesn't appear — no detail is
     * lost, because a relevant pattern will have high similarity and will rank high.
     *
     * @param queryEmbedding 768-dimensional embedding of the current task
     * @param language       optional language filter
     * @param topK           maximum number of global standards to return
     * @return global standards ranked by task-relevance × success_rate, above threshold
     */
    public List<RetrievedPattern> searchGlobalStandards(float[] queryEmbedding, String language, int topK) {
        String vectorStr = VectorUtils.toPostgresVector(queryEmbedding);
        double minSimilarity = retrievalProperties.getMinSimilarityThreshold();

        Condition filters = PATTERNS.IS_GLOBAL_STANDARD.isTrue()
            .and(DSL.field("1 - (embedding <=> ?::vector)", Double.class, vectorStr)
                .greaterOrEqual(minSimilarity));

        if (Objects.nonNull(language) && !language.isBlank()) {
            filters = filters.and(DSL.field("? = ANY(languages)", Boolean.class, language));
        }

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
            .where(filters)
            // Rank by similarity * success_rate so task-relevant AND reliable patterns win
            .orderBy(DSL.field("(1 - (embedding <=> ?::vector)) * COALESCE(success_rate, 0.5)", vectorStr).desc())
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
                    .workflowId(Objects.nonNull(record.get(PATTERNS.WORKFLOW_ID))
                        ? record.get(PATTERNS.WORKFLOW_ID).toString() : null)
                    .patternData(Map.of("retrieval_reason", "global_standard"))
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

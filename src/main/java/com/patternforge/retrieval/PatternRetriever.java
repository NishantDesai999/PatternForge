package com.patternforge.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jooq.JSONB;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patternforge.config.RetrievalProperties;
import com.patternforge.extraction.EmbeddingService;
import com.patternforge.jooq.tables.records.ConversationalPatternsRecord;
import com.patternforge.jooq.tables.records.PatternsRecord;
import com.patternforge.jooq.tables.records.ProjectsRecord;
import com.patternforge.retrieval.model.RetrievedPattern;
import com.patternforge.retrieval.model.TaskContext;
import com.patternforge.storage.KeywordSearchService;
import com.patternforge.storage.VectorSearchService;
import com.patternforge.storage.repository.ConversationalPatternRepository;
import com.patternforge.storage.repository.PatternRepository;
import com.patternforge.storage.repository.ProjectRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Retrieves relevant patterns based on task context.
 * Uses semantic vector search when Ollama available, falls back to keyword search otherwise.
 * Also includes project-specific and conversational patterns when applicable.
 *
 * <p>After deduplication, patterns are packed greedily by relevance score into a configurable
 * token budget ({@code patternforge.retrieval.max-context-tokens}). This ensures the response
 * stays within the LLM's effective context regardless of how large individual patterns are.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PatternRetriever {

    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final KeywordSearchService keywordSearchService;
    private final ConversationalPatternRepository conversationalPatternRepository;
    private final ProjectRepository projectRepository;
    private final PatternRepository patternRepository;
    private final ObjectMapper objectMapper;
    private final RetrievalProperties retrievalProperties;

    /**
     * Result of a retrieval operation, carrying both the patterns and token accounting.
     */
    public record RetrievalResult(List<RetrievedPattern> patterns, int estimatedTokens) {}

    /**
     * Retrieves relevant patterns for given task context, fitting them within a token budget.
     *
     * <p>Patterns are gathered from four sources (global standards, task-specific search,
     * project patterns, conversational patterns), deduplicated, sorted by relevance, then
     * greedily packed into the configured token budget. This ensures the JSON payload stays
     * predictable regardless of how large individual patterns are.
     *
     * @param taskContext The context describing the development task
     * @param topK Maximum number of patterns to retrieve from semantic/keyword search
     * @param projectPath Optional project path to include project-specific patterns
     * @param conversationId Optional conversation ID to include session patterns
     * @return RetrievalResult with patterns and estimated token usage
     */
    public RetrievalResult retrieve(TaskContext taskContext, int topK, String projectPath, String conversationId) {
        if (Objects.isNull(taskContext)) {
            log.warn("TaskContext is null - returning empty results");
            return new RetrievalResult(List.of(), 0);
        }

        int globalCap = retrievalProperties.getMaxGlobalStandards();
        String query = buildQuery(taskContext);

        // Generate embedding once and reuse for all vector queries (avoids duplicate Ollama calls).
        // Uses search_query: prefix required by nomic-embed-text for query embeddings.
        float[] embedding = null;
        if (embeddingService.isAvailable()) {
            embedding = embeddingService.generateQueryEmbedding(query);
            if (Objects.isNull(embedding)) {
                log.warn("Embedding generation returned null - falling back to keyword search");
            }
        }

        // Step 1: Global standards — task-similarity aware when Ollama is available.
        List<RetrievedPattern> globalStandards;
        if (Objects.nonNull(embedding)) {
            globalStandards = vectorSearchService.searchGlobalStandards(embedding, taskContext.getLanguage(), globalCap);
            log.debug("Retrieved {} task-relevant global standards via vector search (cap={})",
                globalStandards.size(), globalCap);
        } else {
            globalStandards = retrieveTopGlobalStandards(globalCap);
            log.debug("Retrieved {} global standard patterns via success_rate fallback (cap={})",
                globalStandards.size(), globalCap);
        }

        // Step 2: Task-specific patterns — reuse embedding generated above
        List<RetrievedPattern> searchPatterns = retrieveGlobalPatterns(embedding, query, taskContext, topK);
        log.debug("Retrieved {} task-specific patterns (topK={})", searchPatterns.size(), topK);

        // Step 3: Get project-specific patterns if projectPath provided (capped)
        List<RetrievedPattern> projectPatterns = new ArrayList<>();
        if (Objects.nonNull(projectPath) && !projectPath.isBlank()) {
            int projectCap = retrievalProperties.getMaxProjectStandards();
            projectPatterns = retrieveProjectPatterns(projectPath, projectCap);
            log.debug("Retrieved {} project-specific patterns (cap={})", projectPatterns.size(), projectCap);
        }

        // Step 4: Get conversational patterns if conversationId provided
        List<RetrievedPattern> conversationalPatterns = new ArrayList<>();
        if (Objects.nonNull(conversationId) && !conversationId.isBlank()) {
            conversationalPatterns = retrieveConversationalPatterns(conversationId);
            log.debug("Retrieved {} conversational patterns", conversationalPatterns.size());
        }

        // Step 5: Combine, deduplicate, and fit within token budget
        List<RetrievedPattern> deduplicated = deduplicatePatterns(globalStandards, searchPatterns, projectPatterns, conversationalPatterns);
        return fitWithinTokenBudget(deduplicated);
    }

    /**
     * Legacy method for backward compatibility.
     * Retrieves top-k most relevant patterns using only global search.
     *
     * @param taskContext The context describing the development task
     * @param topK Maximum number of patterns to retrieve
     * @return RetrievalResult with patterns and estimated token usage
     */
    public RetrievalResult retrieve(TaskContext taskContext, int topK) {
        return retrieve(taskContext, topK, null, null);
    }

    /**
     * Retrieves global patterns using the pre-computed embedding (or keyword fallback).
     * Accepts the embedding generated in {@link #retrieve} to avoid a second Ollama call.
     *
     * @param embedding pre-computed query embedding, or null when Ollama is unavailable
     * @param query     raw query string for keyword fallback
     * @param taskContext task context for filters
     * @param topK      maximum results to return
     */
    private List<RetrievedPattern> retrieveGlobalPatterns(float[] embedding, String query,
                                                           TaskContext taskContext, int topK) {
        if (Objects.nonNull(embedding)) {
            log.info("Using vector search for task-specific pattern retrieval");
            return vectorSearchService.search(embedding, taskContext, topK);
        }

        log.info("Using keyword search for task-specific pattern retrieval");
        return keywordSearchService.search(query, taskContext, topK);
    }

    /**
     * Retrieves up to {@code limit} project-specific patterns for given project path.
     * Includes both conversational patterns promoted to project standards
     * and their linked formal patterns.
     */
    private List<RetrievedPattern> retrieveProjectPatterns(String projectPath, int limit) {
        Optional<ProjectsRecord> projectOpt = projectRepository.findByPath(projectPath);

        if (projectOpt.isEmpty()) {
            log.debug("No project found for path: {}", projectPath);
            return List.of();
        }

        UUID projectId = projectOpt.get().getProjectId();
        List<ConversationalPatternsRecord> conversationalPatterns =
            conversationalPatternRepository.findByProjectId(projectId);

        // Include ALL conversational patterns for this project (is_project_standard is for
        // analytics/tracking only, not a filter gate), but cap total to avoid unbounded growth.
        List<ConversationalPatternsRecord> projectStandards = conversationalPatterns.stream()
            .limit(limit)
            .collect(Collectors.toList());

        log.debug("Found {} project patterns for project {} (cap={})",
            projectStandards.size(), projectId, limit);

        List<RetrievedPattern> results = new ArrayList<>();

        // Add conversational patterns as retrieved patterns
        for (ConversationalPatternsRecord conversationalPattern : projectStandards) {
            results.add(convertConversationalToRetrieved(conversationalPattern, "project_standard"));

            // If linked to formal pattern, retrieve that too
            if (Objects.nonNull(conversationalPattern.getFormalPatternId())) {
                Optional<PatternsRecord> formalPattern =
                    patternRepository.findById(conversationalPattern.getFormalPatternId());

                formalPattern.ifPresent(pattern ->
                    results.add(convertFormalToRetrieved(pattern, "project_standard_formal"))
                );
            }
        }

        return results;
    }

    /**
     * Retrieves conversational patterns from current session.
     */
    private List<RetrievedPattern> retrieveConversationalPatterns(String conversationId) {
        List<ConversationalPatternsRecord> sessionPatterns =
            conversationalPatternRepository.findByConversationId(conversationId);

        log.debug("Found {} conversational patterns for conversation {}",
            sessionPatterns.size(), conversationId);

        return sessionPatterns.stream()
            .map(cp -> convertConversationalToRetrieved(cp, "session_pattern"))
            .collect(Collectors.toList());
    }

    /**
     * Fetches the top {@code limit} global standard patterns ordered by success_rate DESC.
     * Relevance score is set to 1.0 so they sort before task-specific results.
     */
    private List<RetrievedPattern> retrieveTopGlobalStandards(int limit) {
        return patternRepository.findGlobalPatterns(limit).stream()
                .map(record -> convertFormalToRetrieved(record, "global_standard"))
                .collect(Collectors.toList());
    }

    /**
     * Packs patterns greedily by relevance score into the configured token budget.
     *
     * <p>Patterns are sorted by relevance (highest first). Each pattern's estimated token
     * cost is computed via {@link TokenEstimator}. Patterns are added until the next one
     * would exceed the budget. If budgeting is disabled ({@code maxContextTokens <= 0}),
     * all patterns are returned with their total token estimate.
     *
     * @param patterns deduplicated candidate patterns
     * @return RetrievalResult with the fitted list and total estimated tokens
     */
    private RetrievalResult fitWithinTokenBudget(List<RetrievedPattern> patterns) {
        int budget = retrievalProperties.getMaxContextTokens();

        // Sort by relevance descending so highest-value patterns are packed first
        List<RetrievedPattern> sorted = patterns.stream()
                .sorted(Comparator.comparingDouble(RetrievedPattern::getRelevanceScore).reversed())
                .collect(Collectors.toList());

        // If budgeting is disabled, return everything with token accounting
        if (budget <= 0) {
            int totalTokens = sorted.stream().mapToInt(TokenEstimator::estimate).sum();
            log.info("Token budgeting disabled — returning all {} patterns (~{} tokens)",
                    sorted.size(), totalTokens);
            return new RetrievalResult(sorted, totalTokens);
        }

        List<RetrievedPattern> fitted = new ArrayList<>();
        int usedTokens = 0;

        for (RetrievedPattern pattern : sorted) {
            int cost = TokenEstimator.estimate(pattern);
            if (usedTokens + cost > budget) {
                log.debug("Token budget exhausted at {} patterns (~{} tokens, budget={}). " +
                          "Skipping '{}' (~{} tokens)",
                        fitted.size(), usedTokens, budget, pattern.getTitle(), cost);
                break;
            }
            fitted.add(pattern);
            usedTokens += cost;
        }

        if (fitted.size() < sorted.size()) {
            log.info("Token budget: packed {} of {} patterns (~{}/{} tokens, dropped {})",
                    fitted.size(), sorted.size(), usedTokens, budget,
                    sorted.size() - fitted.size());
        } else {
            log.debug("Token budget: all {} patterns fit (~{}/{} tokens)",
                    fitted.size(), usedTokens, budget);
        }

        return new RetrievalResult(fitted, usedTokens);
    }

    /**
     * Deduplicates patterns by pattern_id.
     * Insertion order: global standards → search results → project standards → conversational.
     * Later entries override earlier ones on collision so the highest-context source wins.
     */
    private List<RetrievedPattern> deduplicatePatterns(
            List<RetrievedPattern> globalStandards,
            List<RetrievedPattern> searchPatterns,
            List<RetrievedPattern> projectPatterns,
            List<RetrievedPattern> conversationalPatterns) {

        Map<String, RetrievedPattern> patternMap = new LinkedHashMap<>();

        globalStandards.forEach(p -> {
            if (Objects.nonNull(p.getPatternId())) patternMap.put(p.getPatternId(), p);
        });
        searchPatterns.forEach(p -> {
            if (Objects.nonNull(p.getPatternId())) patternMap.put(p.getPatternId(), p);
        });
        projectPatterns.forEach(p -> {
            if (Objects.nonNull(p.getPatternId())) patternMap.put(p.getPatternId(), p);
        });
        conversationalPatterns.forEach(p -> {
            if (Objects.nonNull(p.getPatternId())) patternMap.put(p.getPatternId(), p);
        });

        List<RetrievedPattern> deduplicated = new ArrayList<>(patternMap.values());
        log.info("Deduplicated patterns: {} total (globalStandards={}, search={}, project={}, conversational={})",
                deduplicated.size(), globalStandards.size(), searchPatterns.size(),
                projectPatterns.size(), conversationalPatterns.size());

        return deduplicated;
    }

    /**
     * Converts a ConversationalPatternsRecord to RetrievedPattern format.
     */
    private RetrievedPattern convertConversationalToRetrieved(
            ConversationalPatternsRecord conversationalPattern,
            String retrievalReason) {

        // Use conversation pattern ID as pattern_id if no formal pattern linked
        String patternId = Objects.nonNull(conversationalPattern.getFormalPatternId())
            ? conversationalPattern.getFormalPatternId().toString()
            : conversationalPattern.getId().toString();

        // Create code examples map if code_example exists
        Map<String, String> codeExamples = new HashMap<>();
        if (Objects.nonNull(conversationalPattern.getCodeExample())
                && !conversationalPattern.getCodeExample().isBlank()) {
            codeExamples.put("example", conversationalPattern.getCodeExample());
        }

        return RetrievedPattern.builder()
            .patternId(patternId)
            .patternName("conversational_" + conversationalPattern.getId())
            .title(retrievalReason.replace("_", " ").toUpperCase())
            .description(conversationalPattern.getDescription())
            .category("conversational")
            .whenToUse(Objects.nonNull(conversationalPattern.getRationale())
                ? conversationalPattern.getRationale()
                : "Project-specific or session-specific pattern")
            .codeExamples(codeExamples)
            .relevanceScore(1.0)  // High priority
            .successRate(Objects.nonNull(conversationalPattern.getConfidence())
                ? conversationalPattern.getConfidence()
                : 0.0)
            .workflowId(null)
            .patternData(Map.of(
                "retrieval_reason", retrievalReason,
                "source", Objects.nonNull(conversationalPattern.getSource())
                    ? conversationalPattern.getSource()
                    : "unknown",
                "promotion_count", Objects.nonNull(conversationalPattern.getPromotionCount())
                    ? conversationalPattern.getPromotionCount()
                    : 0
            ))
            .build();
    }

    /**
     * Converts a PatternsRecord to RetrievedPattern format.
     * Used when retrieving formal patterns linked to conversational patterns.
     */
    private RetrievedPattern convertFormalToRetrieved(PatternsRecord pattern, String retrievalReason) {
        Map<String, String> codeExamples = new HashMap<>();
        if (Objects.nonNull(pattern.getCodeExamples())) {
            codeExamples = parseCodeExamples(pattern.getCodeExamples());
        }

        return RetrievedPattern.builder()
            .patternId(pattern.getPatternId().toString())
            .patternName(pattern.getPatternName())
            .title(pattern.getTitle())
            .description(pattern.getDescription())
            .category(pattern.getCategory())
            .whenToUse(pattern.getWhenToUse())
            .codeExamples(codeExamples)
            .relevanceScore(1.0)  // High priority for project standards
            .successRate(Objects.nonNull(pattern.getSuccessRate())
                ? pattern.getSuccessRate()
                : 0.0)
            .workflowId(Objects.nonNull(pattern.getWorkflowId())
                ? pattern.getWorkflowId().toString()
                : null)
            .patternData(Map.of("retrieval_reason", retrievalReason))
            .build();
    }

    /**
     * Parses JSONB code examples to Map<String, String>.
     */
    private Map<String, String> parseCodeExamples(JSONB codeExamplesJsonb) {
        try {
            return objectMapper.readValue(
                codeExamplesJsonb.data(),
                new TypeReference<Map<String, String>>() {}
            );
        } catch (Exception exception) {
            log.warn("Failed to parse code examples: {}", exception.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Builds searchable query text from task context.
     * Uses the raw task description as the primary semantic signal. Appending heuristic
     * keywords (taskType, components, concerns) was found to degrade embedding quality
     * by pushing the vector away from the user's actual intent.
     *
     * <p>Language/framework are still appended when present because they are strong
     * disambiguation signals (e.g., "java" vs "python" patterns) without adding noise.
     *
     * @param taskContext The task context to convert
     * @return Query string for search
     */
    private String buildQuery(TaskContext taskContext) {
        StringBuilder queryBuilder = new StringBuilder();

        if (Objects.nonNull(taskContext.getDescription()) && !taskContext.getDescription().isBlank()) {
            queryBuilder.append(taskContext.getDescription());
        }

        if (Objects.nonNull(taskContext.getLanguage())) {
            queryBuilder.append(" ").append(taskContext.getLanguage());
        }

        if (Objects.nonNull(taskContext.getFramework())) {
            queryBuilder.append(" ").append(taskContext.getFramework());
        }

        return queryBuilder.toString().trim();
    }
}

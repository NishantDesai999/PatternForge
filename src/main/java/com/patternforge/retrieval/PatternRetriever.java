package com.patternforge.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 * <h3>Dynamic Context Pruning (DCP)</h3>
 * <p>Three DCP mechanisms are applied after deduplication:
 * <ol>
 *   <li><b>Conversation-turn awareness</b> — patterns already sent in this conversation are
 *       skipped unless their relevance score meets the re-injection threshold.
 *   <li><b>Adaptive token budget</b> — when the caller supplies {@code remainingContextTokens},
 *       the effective budget is {@code min(maxContextTokens, remainingContextTokens × adaptiveFraction)}.
 *   <li><b>Negative pruning signal</b> — when a task shift is detected (current embedding vs
 *       previous turn below {@code taskShiftThreshold}), the response includes {@code dropPatternIds}
 *       listing previously sent patterns that are no longer relevant.
 * </ol>
 *
 * <p>After deduplication, patterns are packed greedily by relevance score into the effective
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
    private final ConversationContextTracker conversationContextTracker;

    /**
     * Result of a retrieval operation, carrying patterns, token accounting, and DCP signals.
     */
    public record RetrievalResult(
            List<RetrievedPattern> patterns,
            int estimatedTokens,
            int effectiveBudget,
            List<String> dropPatternIds) {}

    /**
     * Retrieves relevant patterns for given task context, applying Dynamic Context Pruning.
     *
     * <p>Patterns are gathered from four sources (global standards, task-specific search,
     * project patterns, conversational patterns), deduplicated, filtered by DCP turn-awareness,
     * then greedily packed into the effective token budget.
     *
     * @param taskContext            the context describing the development task
     * @param topK                   maximum number of patterns to retrieve from semantic/keyword search
     * @param projectPath            optional project path to include project-specific patterns
     * @param conversationId         optional conversation ID to include session patterns and enable DCP
     * @param remainingContextTokens optional agent remaining context; drives adaptive budget when provided
     * @return RetrievalResult with patterns, token usage, effective budget, and drop signals
     */
    public RetrievalResult retrieve(TaskContext taskContext, int topK, String projectPath,
                                    String conversationId, Integer remainingContextTokens) {
        if (Objects.isNull(taskContext)) {
            log.warn("TaskContext is null - returning empty results");
            return new RetrievalResult(List.of(), 0, 0, List.of());
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

        // Step 5: Combine and deduplicate
        List<RetrievedPattern> deduplicated = deduplicatePatterns(
            globalStandards, searchPatterns, projectPatterns, conversationalPatterns);

        // Step 6: DCP — filter already-sent patterns (conversation-turn awareness)
        int effective = computeEffectiveBudget(remainingContextTokens);
        if (Objects.nonNull(conversationId) && !conversationId.isBlank()) {
            deduplicated = applyTurnAwarenessFilter(deduplicated, conversationId);
        }

        // Step 7: Fit within the effective token budget
        RetrievalResult budgetResult = fitWithinTokenBudget(deduplicated, effective);

        // Step 8: DCP — compute drop list (negative pruning) using task embedding comparison
        List<String> dropIds = computeDropList(conversationId, embedding, budgetResult.patterns());

        // Step 9: Update tracker with newly sent pattern IDs and current task embedding
        if (Objects.nonNull(conversationId) && !conversationId.isBlank()) {
            Set<String> nowSent = budgetResult.patterns().stream()
                .map(RetrievedPattern::getPatternId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            conversationContextTracker.update(conversationId, nowSent, embedding);
        }

        return new RetrievalResult(budgetResult.patterns(), budgetResult.estimatedTokens(), effective, dropIds);
    }

    /**
     * Overload that accepts projectPath and conversationId without remainingContextTokens.
     */
    public RetrievalResult retrieve(TaskContext taskContext, int topK, String projectPath, String conversationId) {
        return retrieve(taskContext, topK, projectPath, conversationId, null);
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
        return retrieve(taskContext, topK, null, null, null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DCP helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Computes the effective token budget.
     *
     * <p>When {@code remainingContextTokens} is supplied the budget is
     * {@code min(configured, remainingContextTokens × adaptiveContextFraction)}.
     * This prevents patterns from consuming an outsized share of an already-full context.
     */
    int computeEffectiveBudget(Integer remainingContextTokens) {
        int configured = retrievalProperties.getMaxContextTokens();
        if (remainingContextTokens == null || remainingContextTokens <= 0 || configured <= 0) {
            return configured;
        }
        int adaptive = (int) (remainingContextTokens * retrievalProperties.getAdaptiveContextFraction());
        int effective = Math.min(configured, adaptive);
        log.debug("DCP adaptive budget: configured={}, remaining={}, fraction={}, effective={}",
            configured, remainingContextTokens, retrievalProperties.getAdaptiveContextFraction(), effective);
        return effective;
    }

    /**
     * Filters out already-sent patterns that don't meet the re-injection score threshold.
     *
     * <p>A pattern that was sent in a previous turn of this conversation is skipped unless
     * its current relevance score is at or above {@code reinjectionScoreThreshold}.
     */
    private List<RetrievedPattern> applyTurnAwarenessFilter(List<RetrievedPattern> patterns, String conversationId) {
        Set<String> sent = conversationContextTracker.getSentPatternIds(conversationId);
        if (sent.isEmpty()) {
            return patterns;
        }
        double threshold = retrievalProperties.getReinjectionScoreThreshold();
        List<RetrievedPattern> filtered = patterns.stream()
            .filter(p -> p.getPatternId() == null
                      || !sent.contains(p.getPatternId())
                      || p.getRelevanceScore() >= threshold)
            .collect(Collectors.toList());
        int skipped = patterns.size() - filtered.size();
        if (skipped > 0) {
            log.debug("DCP turn-awareness: skipped {} already-sent pattern(s) for conversation {}",
                skipped, conversationId);
        }
        return filtered;
    }

    /**
     * Computes the list of pattern IDs the agent should drop from its context.
     *
     * <p>A drop list is only produced when:
     * <ul>
     *   <li>A conversationId is present and has a previous turn embedding.
     *   <li>The cosine similarity between the current and previous task embeddings is below
     *       {@code taskShiftThreshold} — indicating a topic change.
     * </ul>
     * In that case, all previously sent pattern IDs that are not in the current result set
     * are returned as candidates for eviction.
     */
    private List<String> computeDropList(String conversationId, float[] currentEmbedding,
                                          List<RetrievedPattern> currentPatterns) {
        if (conversationId == null || conversationId.isBlank() || currentEmbedding == null) {
            return List.of();
        }
        Optional<float[]> prevEmbOpt = conversationContextTracker.getLastTaskEmbedding(conversationId);
        if (prevEmbOpt.isEmpty()) {
            return List.of();
        }
        float similarity = cosineSimilarity(currentEmbedding, prevEmbOpt.get());
        double threshold = retrievalProperties.getTaskShiftThreshold();
        if (similarity >= threshold) {
            log.debug("DCP task-shift: similarity={:.3f} >= threshold={} — no drop signal", similarity, threshold);
            return List.of();
        }
        log.info("DCP task-shift detected (similarity={:.3f} < threshold={}) for conversation {} — computing drop list",
            similarity, threshold, conversationId);
        Set<String> currentIds = currentPatterns.stream()
            .map(RetrievedPattern::getPatternId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        List<String> drops = conversationContextTracker.getSentPatternIds(conversationId).stream()
            .filter(id -> !currentIds.contains(id))
            .collect(Collectors.toList());
        log.info("DCP drop list: {} pattern(s) to evict for conversation {}", drops.size(), conversationId);
        return drops;
    }

    /**
     * Computes cosine similarity between two float vectors.
     * Returns 0.0 when vectors have different lengths or zero magnitude.
     */
    static float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0.0f;
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot   += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0.0f;
        }
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Retrieval helpers
    // ─────────────────────────────────────────────────────────────────────────

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
     * Packs patterns greedily by relevance score into the given token budget.
     *
     * <p>Patterns are sorted by relevance (highest first). Each pattern's estimated token
     * cost is computed via {@link TokenEstimator}. Patterns are added until the next one
     * would exceed the budget. If budgeting is disabled ({@code budget <= 0}),
     * all patterns are returned with their total token estimate.
     *
     * @param patterns candidate patterns (already deduplicated and DCP-filtered)
     * @param budget   effective token budget (may differ from the configured maximum)
     * @return RetrievalResult with the fitted list and total estimated tokens (effectiveBudget=0, dropPatternIds=empty)
     */
    private RetrievalResult fitWithinTokenBudget(List<RetrievedPattern> patterns, int budget) {
        // Sort by relevance descending so highest-value patterns are packed first
        List<RetrievedPattern> sorted = patterns.stream()
                .sorted(Comparator.comparingDouble(RetrievedPattern::getRelevanceScore).reversed())
                .collect(Collectors.toList());

        // If budgeting is disabled, return everything with token accounting
        if (budget <= 0) {
            int totalTokens = sorted.stream().mapToInt(TokenEstimator::estimate).sum();
            log.info("Token budgeting disabled — returning all {} patterns (~{} tokens)",
                    sorted.size(), totalTokens);
            return new RetrievalResult(sorted, totalTokens, 0, List.of());
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

        return new RetrievalResult(fitted, usedTokens, 0, List.of());
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

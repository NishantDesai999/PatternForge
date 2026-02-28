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
 * <p>Global standards are returned in <b>slim</b> format (title + category only, no description
 * or code examples) to minimize token usage. If a global standard is also task-relevant
 * (returned by vector/keyword search), the full-detail version replaces the slim entry during
 * deduplication.
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
     * Retrieves relevant patterns for given task context.
     * Automatically selects vector or keyword search based on embedding service availability.
     * Also includes project-specific and conversational patterns when applicable.
     *
     * <p>Global standards (is_global_standard = true) are always returned in <b>slim</b> format
     * (title + category only) so the LLM sees every rule name without bloating the context.
     * If a global standard is also returned by semantic/keyword search (i.e., it IS task-relevant),
     * the full-detail version replaces the slim one during deduplication.
     *
     * <p>After deduplication, the total pattern count is capped at {@code maxTotalPatterns}
     * (configured via {@code patternforge.retrieval.max-total-patterns}) to provide a hard
     * upper bound on response size and therefore LLM token usage.
     *
     * @param taskContext The context describing the development task
     * @param topK Maximum number of patterns to retrieve from semantic/keyword search
     * @param projectPath Optional project path to include project-specific patterns
     * @param conversationId Optional conversation ID to include session patterns
     * @return List of retrieved patterns ranked by relevance
     */
    public List<RetrievedPattern> retrieve(TaskContext taskContext, int topK, String projectPath, String conversationId) {
        if (Objects.isNull(taskContext)) {
            log.warn("TaskContext is null - returning empty results");
            return List.of();
        }

        // Step 1: Always include ALL global standards in slim format (title + category only)
        List<RetrievedPattern> globalStandards = retrieveSlimGlobalStandards();
        log.debug("Retrieved {} global standard patterns (slim format)", globalStandards.size());

        // Step 2: Get additional task-specific patterns using semantic/keyword search
        List<RetrievedPattern> searchPatterns = retrieveGlobalPatterns(taskContext, topK);
        log.debug("Retrieved {} task-specific patterns (topK={})", searchPatterns.size(), topK);

        // Step 3: Get project-specific patterns if projectPath provided (capped)
        List<RetrievedPattern> projectPatterns = new ArrayList<>();
        if (Objects.nonNull(projectPath) && !projectPath.isBlank()) {
            projectPatterns = retrieveProjectPatterns(projectPath);
            log.debug("Retrieved {} project-specific patterns", projectPatterns.size());
        }

        // Step 4: Get conversational patterns if conversationId provided
        List<RetrievedPattern> conversationalPatterns = new ArrayList<>();
        if (Objects.nonNull(conversationId) && !conversationId.isBlank()) {
            conversationalPatterns = retrieveConversationalPatterns(conversationId);
            log.debug("Retrieved {} conversational patterns", conversationalPatterns.size());
        }

        // Step 5: Combine, deduplicate, and enforce total cap
        List<RetrievedPattern> deduplicated = deduplicatePatterns(globalStandards, searchPatterns, projectPatterns, conversationalPatterns);
        return enforceTotalCap(deduplicated);
    }
    
    /**
     * Legacy method for backward compatibility.
     * Retrieves top-k most relevant patterns using only global search.
     *
     * @param taskContext The context describing the development task
     * @param topK Maximum number of patterns to retrieve
     * @return List of retrieved patterns ranked by relevance
     */
    public List<RetrievedPattern> retrieve(TaskContext taskContext, int topK) {
        return retrieve(taskContext, topK, null, null);
    }
    
    /**
     * Retrieves global patterns using semantic or keyword search.
     * Vector search applies the configured similarity threshold to filter irrelevant results.
     */
    private List<RetrievedPattern> retrieveGlobalPatterns(TaskContext taskContext, int topK) {
        String query = buildQuery(taskContext);
        log.debug("Built query from task context: {}", query);

        if (embeddingService.isAvailable()) {
            log.info("Using vector search for pattern retrieval");
            float[] embedding = embeddingService.generateQueryEmbedding(query);

            if (Objects.nonNull(embedding)) {
                return vectorSearchService.search(
                        embedding, taskContext, topK, retrievalProperties.getSimilarityThreshold());
            }

            log.warn("Embedding generation returned null - falling back to keyword search");
        }

        log.info("Using keyword search for pattern retrieval");
        return keywordSearchService.search(query, taskContext, topK);
    }
    
    /**
     * Retrieves project-specific patterns for given project path.
     * Capped to {@code maxProjectPatterns} most relevant conversational patterns.
     * Includes their linked formal patterns when available.
     */
    private List<RetrievedPattern> retrieveProjectPatterns(String projectPath) {
        Optional<ProjectsRecord> projectOpt = projectRepository.findByPath(projectPath);

        if (projectOpt.isEmpty()) {
            log.debug("No project found for path: {}", projectPath);
            return List.of();
        }

        UUID projectId = projectOpt.get().getProjectId();
        int maxProjectPatterns = retrievalProperties.getMaxProjectPatterns();
        List<ConversationalPatternsRecord> projectStandards =
            conversationalPatternRepository.findByProjectId(projectId, maxProjectPatterns);

        log.debug("Found {} project patterns for project {} (cap={})",
                projectStandards.size(), projectId, maxProjectPatterns);
        
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
     * Fetches ALL patterns marked is_global_standard = true in <b>slim</b> format.
     * Only {@code patternId}, {@code patternName}, {@code title}, and {@code category} are populated.
     * Heavy fields ({@code description}, {@code codeExamples}, {@code whenToUse}) are set to null.
     *
     * <p>This keeps the LLM aware of every standard name while consuming minimal tokens (~200 bytes
     * per pattern vs 3-8KB for full detail). If the pattern also appears in semantic/keyword search
     * results, the full-detail version will overwrite this slim entry during deduplication.
     */
    private List<RetrievedPattern> retrieveSlimGlobalStandards() {
        return patternRepository.findGlobalPatterns().stream()
                .map(record -> RetrievedPattern.builder()
                        .patternId(record.getPatternId().toString())
                        .patternName(record.getPatternName())
                        .title(record.getTitle())
                        .description(null)
                        .category(record.getCategory())
                        .whenToUse(null)
                        .codeExamples(null)
                        .relevanceScore(1.0)
                        .successRate(Objects.nonNull(record.getSuccessRate()) ? record.getSuccessRate() : 0.0)
                        .workflowId(Objects.nonNull(record.getWorkflowId()) ? record.getWorkflowId().toString() : null)
                        .patternData(Map.of("retrieval_reason", "global_standard", "format", "slim"))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Enforces a hard upper limit on total patterns in the response.
     * Keeps patterns sorted by relevance score descending; lowest-relevance patterns are trimmed.
     */
    private List<RetrievedPattern> enforceTotalCap(List<RetrievedPattern> patterns) {
        int maxTotal = retrievalProperties.getMaxTotalPatterns();
        if (maxTotal <= 0 || patterns.size() <= maxTotal) {
            return patterns;
        }

        log.info("Enforcing total cap: {} patterns → {} (trimming {} lowest-relevance)",
                patterns.size(), maxTotal, patterns.size() - maxTotal);

        return patterns.stream()
                .sorted(Comparator.comparingDouble(RetrievedPattern::getRelevanceScore).reversed())
                .limit(maxTotal)
                .collect(Collectors.toList());
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

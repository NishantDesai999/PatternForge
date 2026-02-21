package com.patternforge.retrieval;

import java.util.ArrayList;
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
    
    /**
     * Retrieves top-k most relevant patterns for given task context.
     * Automatically selects vector or keyword search based on embedding service availability.
     * Also includes project-specific and conversational patterns when applicable.
     *
     * @param taskContext The context describing the development task
     * @param topK Maximum number of patterns to retrieve from semantic/keyword search
     * @param projectPath Optional project path to include project-specific patterns
     * @param conversationId Optional conversation ID to include session patterns
     * @return List of retrieved patterns ranked by relevance (may exceed topK if project patterns added)
     */
    public List<RetrievedPattern> retrieve(TaskContext taskContext, int topK, String projectPath, String conversationId) {
        if (Objects.isNull(taskContext)) {
            log.warn("TaskContext is null - returning empty results");
            return List.of();
        }
        
        // Step 1: Get global patterns using semantic/keyword search
        List<RetrievedPattern> globalPatterns = retrieveGlobalPatterns(taskContext, topK);
        log.debug("Retrieved {} global patterns", globalPatterns.size());
        
        // Step 2: Get project-specific patterns if projectPath provided
        List<RetrievedPattern> projectPatterns = new ArrayList<>();
        if (Objects.nonNull(projectPath) && !projectPath.isBlank()) {
            projectPatterns = retrieveProjectPatterns(projectPath);
            log.debug("Retrieved {} project-specific patterns", projectPatterns.size());
        }
        
        // Step 3: Get conversational patterns if conversationId provided
        List<RetrievedPattern> conversationalPatterns = new ArrayList<>();
        if (Objects.nonNull(conversationId) && !conversationId.isBlank()) {
            conversationalPatterns = retrieveConversationalPatterns(conversationId);
            log.debug("Retrieved {} conversational patterns", conversationalPatterns.size());
        }
        
        // Step 4: Combine and deduplicate by pattern_id
        return deduplicatePatterns(globalPatterns, projectPatterns, conversationalPatterns);
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
     */
    private List<RetrievedPattern> retrieveGlobalPatterns(TaskContext taskContext, int topK) {
        String query = buildQuery(taskContext);
        log.debug("Built query from task context: {}", query);
        
        if (embeddingService.isAvailable()) {
            log.info("Using vector search for pattern retrieval");
            float[] embedding = embeddingService.generateEmbedding(query);
            
            if (Objects.nonNull(embedding)) {
                return vectorSearchService.search(embedding, taskContext, topK);
            }
            
            log.warn("Embedding generation returned null - falling back to keyword search");
        }
        
        log.info("Using keyword search for pattern retrieval");
        return keywordSearchService.search(query, taskContext, topK);
    }
    
    /**
     * Retrieves project-specific patterns for given project path.
     * Includes both conversational patterns promoted to project standards
     * and their linked formal patterns.
     */
    private List<RetrievedPattern> retrieveProjectPatterns(String projectPath) {
        Optional<ProjectsRecord> projectOpt = projectRepository.findByPath(projectPath);
        
        if (projectOpt.isEmpty()) {
            log.debug("No project found for path: {}", projectPath);
            return List.of();
        }
        
        UUID projectId = projectOpt.get().getProjectId();
        List<ConversationalPatternsRecord> conversationalPatterns = 
            conversationalPatternRepository.findByProjectId(projectId);
        
        // Filter to project standards only
        List<ConversationalPatternsRecord> projectStandards = conversationalPatterns.stream()
            .filter(cp -> Boolean.TRUE.equals(cp.getIsProjectStandard()))
            .collect(Collectors.toList());
        
        log.debug("Found {} project standard patterns for project {}", projectStandards.size(), projectId);
        
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
     * Deduplicates patterns by pattern_id, preserving order (global, project, conversational).
     * Higher priority patterns (project/conversational) override global patterns if duplicate.
     */
    private List<RetrievedPattern> deduplicatePatterns(
            List<RetrievedPattern> globalPatterns,
            List<RetrievedPattern> projectPatterns,
            List<RetrievedPattern> conversationalPatterns) {
        
        Map<String, RetrievedPattern> patternMap = new LinkedHashMap<>();
        
        // Add in priority order: global first, then project, then conversational
        // This ensures higher priority patterns override lower priority ones
        globalPatterns.forEach(p -> {
            if (Objects.nonNull(p.getPatternId())) {
                patternMap.put(p.getPatternId(), p);
            }
        });
        
        projectPatterns.forEach(p -> {
            if (Objects.nonNull(p.getPatternId())) {
                patternMap.put(p.getPatternId(), p);
            }
        });
        
        conversationalPatterns.forEach(p -> {
            if (Objects.nonNull(p.getPatternId())) {
                patternMap.put(p.getPatternId(), p);
            }
        });
        
        List<RetrievedPattern> deduplicated = new ArrayList<>(patternMap.values());
        log.info("Deduplicated patterns: {} total (global={}, project={}, conversational={})",
            deduplicated.size(), globalPatterns.size(), projectPatterns.size(), conversationalPatterns.size());
        
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
     * Builds searchable query text from task context components.
     * Combines taskType, components, and concerns into single searchable string.
     *
     * @param taskContext The task context to convert
     * @return Query string for search
     */
    private String buildQuery(TaskContext taskContext) {
        StringBuilder queryBuilder = new StringBuilder();
        
        // CRITICAL: Include original description for better keyword matching
        if (Objects.nonNull(taskContext.getDescription()) && !taskContext.getDescription().isBlank()) {
            queryBuilder.append(taskContext.getDescription()).append(" ");
        }
        
        if (Objects.nonNull(taskContext.getTaskType())) {
            queryBuilder.append(taskContext.getTaskType()).append(" ");
        }
        
        if (Objects.nonNull(taskContext.getComponents()) && !taskContext.getComponents().isEmpty()) {
            String componentsText = String.join(" ", taskContext.getComponents());
            queryBuilder.append(componentsText).append(" ");
        }
        
        if (Objects.nonNull(taskContext.getConcerns()) && !taskContext.getConcerns().isEmpty()) {
            String concernsText = String.join(" ", taskContext.getConcerns());
            queryBuilder.append(concernsText).append(" ");
        }
        
        if (Objects.nonNull(taskContext.getLanguage())) {
            queryBuilder.append(taskContext.getLanguage()).append(" ");
        }
        
        if (Objects.nonNull(taskContext.getFramework())) {
            queryBuilder.append(taskContext.getFramework());
        }
        
        return queryBuilder.toString().trim();
    }
}

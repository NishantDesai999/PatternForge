package com.patternforge.api.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patternforge.api.dto.PatternDto;
import com.patternforge.api.dto.PatternExtractionRequest;
import com.patternforge.api.dto.PatternUsageRequest;
import com.patternforge.extraction.EmbeddingService;
import com.patternforge.jooq.tables.records.PatternsRecord;
import com.patternforge.llm.AnthropicApiException;
import com.patternforge.llm.PatternExtractionService;
import com.patternforge.retrieval.PatternRetriever;
import com.patternforge.retrieval.TaskAnalyzer;
import com.patternforge.retrieval.model.RetrievedPattern;
import com.patternforge.retrieval.model.TaskContext;
import com.patternforge.storage.repository.PatternRepository;
import com.patternforge.usage.PatternUsageService;
import com.patternforge.workflow.WorkflowResolver;
import com.patternforge.workflow.model.WorkflowResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * REST controller for pattern queries and retrieval.
 */
@RestController
@RequestMapping("/api/patterns")
@RequiredArgsConstructor
@Slf4j
public class PatternController {
    
    private final PatternRepository patternRepository;
    private final TaskAnalyzer taskAnalyzer;
    private final PatternRetriever patternRetriever;
    private final WorkflowResolver workflowResolver;
    private final EmbeddingService embeddingService;
    private final PatternUsageService patternUsageService;
    private final com.patternforge.promotion.PatternPromotionService patternPromotionService;
    private final PatternExtractionService patternExtractionService;
    private final ObjectMapper objectMapper;
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("PatternForge API is running");
    }
    
    @GetMapping
    public ResponseEntity<List<PatternDto>> getAllPatterns() {
        log.info("Fetching all patterns");
        List<PatternsRecord> patterns = patternRepository.findAll();
        List<PatternDto> dtos = patterns.stream()
            .map(this::toDto)
            .toList();
        return ResponseEntity.ok(dtos);
    }
    
    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> queryPatterns(@RequestBody Map<String, Object> request) {
        String task = (String) request.get("task");
        String language = (String) request.get("language");
        String projectPath = (String) request.get("projectPath");
        String conversationId = (String) request.get("conversationId");
        Integer topK = Objects.nonNull(request.get("topK")) ? (Integer) request.get("topK") : 7;
        
        log.info("Querying patterns - task: {}, language: {}, projectPath: {}, conversationId: {}, topK: {}", 
            task, language, projectPath, conversationId, topK);
        
        // 1. Analyze task
        TaskContext taskContext = taskAnalyzer.analyze(task, language, null);
        
        // 2. Retrieve patterns (including project-specific and conversational patterns)
        List<RetrievedPattern> patterns = patternRetriever.retrieve(taskContext, topK, projectPath, conversationId);
        
        // 3. Resolve workflow
        WorkflowResponse workflow = workflowResolver.resolveWorkflow(
            taskContext.getTaskType(),
            task,
            projectPath,
            patterns
        );
        
        // 4. Build response
        return ResponseEntity.ok(Map.of(
            "patterns", patterns,
            "workflow", workflow,
            "metadata", Map.of(
                "patterns_retrieved", patterns.size(),
                "task_type", taskContext.getTaskType(),
                "search_strategy", embeddingService.isAvailable() ? "semantic" : "keyword"
            )
        ));
    }
    
    @PostMapping("/usage")
    public ResponseEntity<Map<String, Object>> recordPatternUsage(@RequestBody PatternUsageRequest request) {
        log.info("Recording pattern usage: patternId={}, projectPath={}, taskType={}, success={}", 
            request.getPatternId(), request.getProjectPath(), request.getTaskType(), request.getSuccess());
        
        try {
            UUID usageId = patternUsageService.recordUsage(request);
            Double successRate = patternUsageService.calculateSuccessRate(request.getPatternId());
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "usage_id", usageId.toString(),
                "success_rate", Objects.nonNull(successRate) ? successRate : 0.0,
                "message", "Pattern usage recorded successfully"
            ));
        } catch (IllegalArgumentException illegalArgumentException) {
            log.warn("Invalid pattern usage request: {}", illegalArgumentException.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", illegalArgumentException.getMessage()
            ));
        } catch (Exception exception) {
            log.error("Error recording pattern usage", exception);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Internal server error: " + exception.getMessage()
            ));
        }
    }
    
    @PostMapping("/extract")
    public ResponseEntity<Map<String, Object>> extractPatterns(@RequestBody PatternExtractionRequest request) {
        log.info("Extracting patterns from file={}", request.getFilePath());

        try {
            List<PatternDto> extracted = patternExtractionService.extract(request);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "patterns_extracted", extracted.size(),
                "patterns", extracted,
                "source_file", Objects.requireNonNullElse(request.getFilePath(), "")
            ));
        } catch (IllegalArgumentException illegalArgumentException) {
            log.warn("Invalid extraction request: {}", illegalArgumentException.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", illegalArgumentException.getMessage()
            ));
        } catch (AnthropicApiException anthropicApiException) {
            log.error("LLM extraction failed", anthropicApiException);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "LLM extraction failed: " + anthropicApiException.getMessage()
            ));
        } catch (Exception exception) {
            log.error("Unexpected error during pattern extraction", exception);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Internal server error: " + exception.getMessage()
            ));
        }
    }

    @PostMapping("/admin/generate-embeddings")
    public ResponseEntity<Map<String, Object>> generateEmbeddings() {
        log.info("Generating embeddings for all patterns");
        
        if (!embeddingService.isAvailable()) {
            return ResponseEntity.ok(Map.of(
                "status", "error",
                "message", "Ollama embedding service is not available"
            ));
        }
        
        List<PatternsRecord> patterns = patternRepository.findAll();
        int successCount = 0;
        int failureCount = 0;
        
        for (PatternsRecord pattern : patterns) {
            try {
                String embeddingText = buildEmbeddingText(pattern);
                float[] embedding = embeddingService.generateEmbedding(embeddingText);
                
                if (Objects.nonNull(embedding)) {
                    patternRepository.updateEmbedding(pattern.getPatternId(), embedding);
                    successCount++;
                    log.info("Generated embedding for pattern: {} ({})", pattern.getPatternName(), pattern.getPatternId());
                } else {
                    failureCount++;
                    log.warn("Failed to generate embedding for pattern: {}", pattern.getPatternId());
                }
            } catch (Exception exception) {
                failureCount++;
                log.error("Error generating embedding for pattern: {}", pattern.getPatternId(), exception);
            }
        }
        
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "total_patterns", patterns.size(),
            "success_count", successCount,
            "failure_count", failureCount,
            "message", String.format("Generated %d embeddings successfully, %d failures", successCount, failureCount)
        ));
    }
    
    private String buildEmbeddingText(PatternsRecord pattern) {
        StringBuilder embeddingText = new StringBuilder();
        embeddingText.append("Title: ").append(pattern.getTitle()).append("\n");
        embeddingText.append("Description: ").append(pattern.getDescription()).append("\n");
        
        if (Objects.nonNull(pattern.getWhenToUse())) {
            embeddingText.append("When to use: ").append(pattern.getWhenToUse()).append("\n");
        }
        
        if (Objects.nonNull(pattern.getCategory())) {
            embeddingText.append("Category: ").append(pattern.getCategory()).append("\n");
        }
        
        return embeddingText.toString();
    }
    
    private PatternDto toDto(PatternsRecord record) {
        Map<String, Object> codeExamples = null;
        if (Objects.nonNull(record.getCodeExamples())) {
            try {
                codeExamples = objectMapper.readValue(
                    record.getCodeExamples().data(),
                    new TypeReference<Map<String, Object>>() {}
                );
            } catch (Exception exception) {
                log.warn("Failed to parse code examples for pattern: {}", record.getPatternId(), exception);
            }
        }
        
        List<String> languages = null;
        if (Objects.nonNull(record.getLanguages())) {
            languages = Arrays.asList(record.getLanguages());
        }
        
        return PatternDto.builder()
            .patternId(record.getPatternId())
            .patternName(record.getPatternName())
            .title(record.getTitle())
            .description(record.getDescription())
            .category(record.getCategory())
            .scope(record.getScope())
            .languages(languages)
            .whenToUse(record.getWhenToUse())
            .codeExamples(codeExamples)
            .successRate(record.getSuccessRate())
            .usageCount(record.getUsageCount())
            .isGlobalStandard(record.getIsGlobalStandard())
            .build();
    }
    
    @PostMapping("/admin/promote-patterns")
    public ResponseEntity<Map<String, Object>> promotePatterns() {
        log.info("Running pattern promotion check");
        
        try {
            int promotedCount = patternPromotionService.checkAndPromotePatterns();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "promoted_count", promotedCount,
                "message", String.format("Promoted %d patterns to project standard", promotedCount)
            ));
        } catch (Exception exception) {
            log.error("Failed to promote patterns", exception);
            return ResponseEntity.ok(Map.of(
                "status", "error",
                "message", "Failed to promote patterns: " + exception.getMessage()
            ));
        }
    }
}

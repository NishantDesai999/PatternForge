package com.patternforge.api.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patternforge.api.dto.*;
import com.patternforge.config.RetrievalProperties;
import com.patternforge.extraction.EmbeddingService;
import com.patternforge.jooq.tables.records.PatternsRecord;
import com.patternforge.llm.AnthropicApiException;
import com.patternforge.llm.PatternExtractionService;
import com.patternforge.promotion.PatternPromotionService;
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
import org.springframework.web.bind.annotation.*;

import java.util.*;

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
    private final PatternPromotionService patternPromotionService;
    private final PatternExtractionService patternExtractionService;
    private final RetrievalProperties retrievalProperties;
    private final ObjectMapper objectMapper;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("PatternForge API is running");
    }

    @GetMapping
    public ResponseEntity<List<PatternDto>> getAllPatterns() {
        log.info("Fetching all patterns");
        List<PatternDto> dtos = patternRepository.findAll().stream()
            .map(this::toDto)
            .toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/query")
    public ResponseEntity<PatternQueryResponse> queryPatterns(@RequestBody PatternQueryRequest request) {
        int topK = Objects.nonNull(request.topK()) ? request.topK() : 10;

        log.info("Querying patterns - task: {}, language: {}, projectPath: {}, conversationId: {}, topK: {}",
            request.task(), request.language(), request.projectPath(), request.conversationId(), topK);

        TaskContext taskContext = taskAnalyzer.analyze(request.task(), request.language(), null);

        PatternRetriever.RetrievalResult result = patternRetriever.retrieve(
            taskContext, topK, request.projectPath(), request.conversationId());

        List<RetrievedPattern> patterns = result.patterns();

        WorkflowResponse workflow = workflowResolver.resolveWorkflow(
            taskContext.getTaskType(), request.task(), request.projectPath(), patterns);

        QueryMetadata metadata = new QueryMetadata(
            patterns.size(),
            taskContext.getTaskType(),
            embeddingService.isAvailable() ? "semantic" : "keyword",
            result.estimatedTokens(),
            retrievalProperties.getMaxContextTokens());

        return ResponseEntity.ok(new PatternQueryResponse(patterns, workflow, metadata));
    }

    @PostMapping("/usage")
    public ResponseEntity<?> recordPatternUsage(@RequestBody PatternUsageRequest request) {
        log.info("Recording pattern usage: patternId={}, projectPath={}, taskType={}, success={}",
            request.getPatternId(), request.getProjectPath(), request.getTaskType(), request.getSuccess());

        try {
            UUID usageId = patternUsageService.recordUsage(request);
            Double successRate = patternUsageService.calculateSuccessRate(request.getPatternId());

            return ResponseEntity.ok(new PatternUsageResponse(
                "success",
                usageId.toString(),
                Objects.nonNull(successRate) ? successRate : 0.0,
                "Pattern usage recorded successfully"));

        } catch (IllegalArgumentException illegalArgumentException) {
            log.warn("Invalid pattern usage request: {}", illegalArgumentException.getMessage());
            return ResponseEntity.badRequest().body(ApiErrorResponse.of(illegalArgumentException.getMessage()));
        } catch (Exception exception) {
            log.error("Error recording pattern usage", exception);
            return ResponseEntity.internalServerError()
                .body(ApiErrorResponse.of("Internal server error: " + exception.getMessage()));
        }
    }

    @PostMapping("/extract")
    public ResponseEntity<?> extractPatterns(@RequestBody PatternExtractionRequest request) {
        log.info("Extracting patterns from file={}", request.getFilePath());

        try {
            List<PatternDto> extracted = patternExtractionService.extract(request);
            return ResponseEntity.ok(new PatternExtractionResponse(
                "success",
                extracted.size(),
                extracted,
                Objects.requireNonNullElse(request.getFilePath(), "")));

        } catch (IllegalArgumentException illegalArgumentException) {
            log.warn("Invalid extraction request: {}", illegalArgumentException.getMessage());
            return ResponseEntity.badRequest().body(ApiErrorResponse.of(illegalArgumentException.getMessage()));
        } catch (AnthropicApiException anthropicApiException) {
            log.error("LLM extraction failed", anthropicApiException);
            return ResponseEntity.internalServerError()
                .body(ApiErrorResponse.of("LLM extraction failed: " + anthropicApiException.getMessage()));
        } catch (Exception exception) {
            log.error("Unexpected error during pattern extraction", exception);
            return ResponseEntity.internalServerError()
                .body(ApiErrorResponse.of("Internal server error: " + exception.getMessage()));
        }
    }

    @PostMapping("/admin/generate-embeddings")
    public ResponseEntity<EmbeddingGenerationResponse> generateEmbeddings() {
        log.info("Generating embeddings for all patterns");

        if (!embeddingService.isAvailable()) {
            return ResponseEntity.ok(new EmbeddingGenerationResponse(
                "error", 0, 0, 0, "Ollama embedding service is not available"));
        }

        List<PatternsRecord> patterns = patternRepository.findAll();
        int successCount = 0;
        int failureCount = 0;

        for (PatternsRecord pattern : patterns) {
            try {
                String embeddingText = buildEmbeddingText(pattern);
                float[] embedding = embeddingService.generateDocumentEmbedding(embeddingText);

                if (Objects.nonNull(embedding)) {
                    patternRepository.updateEmbedding(pattern.getPatternId(), embedding);
                    successCount++;
                    log.info("Generated embedding for pattern: {} ({})",
                        pattern.getPatternName(), pattern.getPatternId());
                } else {
                    failureCount++;
                    log.warn("Failed to generate embedding for pattern: {}", pattern.getPatternId());
                }
            } catch (Exception exception) {
                failureCount++;
                log.error("Error generating embedding for pattern: {}", pattern.getPatternId(), exception);
            }
        }

        return ResponseEntity.ok(new EmbeddingGenerationResponse(
            "success",
            patterns.size(),
            successCount,
            failureCount,
            String.format("Generated %d embeddings successfully, %d failures", successCount, failureCount)));
    }

    @PostMapping("/admin/promote-patterns")
    public ResponseEntity<PromotionResponse> promotePatterns() {
        log.info("Running pattern promotion check (conversational→project→global)");

        try {
            int toProjectCount = patternPromotionService.checkAndPromotePatterns();
            int toGlobalCount = patternPromotionService.checkAndPromoteToGlobal();

            return ResponseEntity.ok(new PromotionResponse(
                "success",
                toProjectCount,
                toGlobalCount,
                toProjectCount + toGlobalCount,
                String.format("Promoted %d pattern(s) to project standard, %d to global standard",
                    toProjectCount, toGlobalCount)));

        } catch (Exception exception) {
            log.error("Failed to promote patterns", exception);
            return ResponseEntity.internalServerError().body(new PromotionResponse(
                "error", 0, 0, 0, "Failed to promote patterns: " + exception.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────

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

    PatternDto toDto(PatternsRecord record) {
        Map<String, String> codeExamples = null;
        if (Objects.nonNull(record.getCodeExamples())) {
            try {
                codeExamples = objectMapper.readValue(
                    record.getCodeExamples().data(),
                    new TypeReference<Map<String, String>>() {});
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
}

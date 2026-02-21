package com.patternforge.usage;

import com.patternforge.api.dto.PatternUsageRequest;
import com.patternforge.jooq.tables.records.ConversationalPatternsRecord;
import com.patternforge.jooq.tables.records.PatternUsageRecord;
import com.patternforge.jooq.tables.records.ProjectsRecord;
import com.patternforge.storage.repository.ConversationalPatternRepository;
import com.patternforge.storage.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.patternforge.jooq.Tables.CONVERSATIONAL_PATTERNS;
import static com.patternforge.jooq.Tables.PATTERNS;
import static com.patternforge.jooq.Tables.PATTERN_USAGE;

/**
 * Service for tracking pattern usage and calculating success metrics.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PatternUsageService {
    
    private final ProjectRepository projectRepository;
    private final ConversationalPatternRepository conversationalPatternRepository;
    private final DSLContext dsl;
    
    /**
     * Records pattern usage and updates pattern metrics.
     * 
     * @param request Pattern usage tracking request
     * @return UUID of the created usage record
     */
    public UUID recordUsage(PatternUsageRequest request) {
        validateRequest(request);
        
        // 1. Find or create project
        ProjectsRecord project = findOrCreateProject(request.getProjectPath());
        
        // 2. Insert usage record
        PatternUsageRecord usageRecord = dsl.newRecord(PATTERN_USAGE);
        usageRecord.setPatternId(request.getPatternId());
        usageRecord.setProjectId(project.getProjectId());
        usageRecord.setTaskType(request.getTaskType());
        usageRecord.setTaskDescription(request.getTaskDescription());
        usageRecord.setSuccess(request.getSuccess());
        usageRecord.setCodeQualityScore(request.getCodeQualityScore());
        usageRecord.setIterationsNeeded(request.getIterationsNeeded());
        usageRecord.setTimestamp(LocalDateTime.now());
        usageRecord.insert();
        
        UUID usageId = usageRecord.getUsageId();
        log.info("Recorded pattern usage: patternId={}, projectId={}, success={}, usageId={}", 
            request.getPatternId(), project.getProjectId(), request.getSuccess(), usageId);
        
        // 3. Update pattern metrics
        updatePatternMetrics(request.getPatternId());
        
        // 4. If conversational pattern with success=true, increment promotion count
        if (Objects.nonNull(request.getSuccess()) && request.getSuccess()) {
            incrementConversationalPatternPromotion(request.getPatternId());
        }
        
        return usageId;
    }
    
    /**
     * Calculates success rate for a pattern based on all usage records.
     * 
     * @param patternId Pattern UUID
     * @return Success rate between 0.0 and 1.0, or null if no usage records exist
     */
    public Double calculateSuccessRate(UUID patternId) {
        if (Objects.isNull(patternId)) {
            return null;
        }
        
        Double successRate = dsl.select(
                DSL.count().filterWhere(PATTERN_USAGE.SUCCESS.isTrue()).cast(Double.class)
                    .div(DSL.count().cast(Double.class))
            )
            .from(PATTERN_USAGE)
            .where(PATTERN_USAGE.PATTERN_ID.eq(patternId))
            .fetchOne(0, Double.class);
        
        log.debug("Calculated success rate for pattern {}: {}", patternId, successRate);
        return successRate;
    }
    
    /**
     * Updates pattern success_rate, usage_count, and last_used timestamp.
     */
    private void updatePatternMetrics(UUID patternId) {
        Double successRate = calculateSuccessRate(patternId);
        
        Integer usageCount = dsl.selectCount()
            .from(PATTERN_USAGE)
            .where(PATTERN_USAGE.PATTERN_ID.eq(patternId))
            .fetchOne(0, Integer.class);
        
        dsl.update(PATTERNS)
            .set(PATTERNS.SUCCESS_RATE, successRate)
            .set(PATTERNS.USAGE_COUNT, usageCount)
            .set(PATTERNS.LAST_USED, LocalDateTime.now())
            .where(PATTERNS.PATTERN_ID.eq(patternId))
            .execute();
        
        log.debug("Updated pattern metrics: patternId={}, successRate={}, usageCount={}", 
            patternId, successRate, usageCount);
    }
    
    /**
     * Increments promotion count for conversational patterns.
     */
    private void incrementConversationalPatternPromotion(UUID patternId) {
        List<ConversationalPatternsRecord> conversationalPatterns = dsl.selectFrom(CONVERSATIONAL_PATTERNS)
            .where(CONVERSATIONAL_PATTERNS.FORMAL_PATTERN_ID.eq(patternId))
            .fetch();
        
        if (conversationalPatterns.isEmpty()) {
            log.debug("Pattern {} is not a conversational pattern, skipping promotion increment", patternId);
            return;
        }
        
        for (ConversationalPatternsRecord conversationalPattern : conversationalPatterns) {
            conversationalPatternRepository.incrementPromotionCount(conversationalPattern.getId());
            log.info("Incremented promotion count for conversational pattern: id={}, patternId={}", 
                conversationalPattern.getId(), patternId);
        }
    }
    
    /**
     * Finds or creates a project record from project path.
     */
    private ProjectsRecord findOrCreateProject(String projectPath) {
        if (Objects.isNull(projectPath) || projectPath.isBlank()) {
            throw new IllegalArgumentException("Project path cannot be null or blank");
        }
        
        Optional<ProjectsRecord> existing = projectRepository.findByPath(projectPath);
        if (existing.isPresent()) {
            return existing.get();
        }
        
        // Extract project name from path
        String projectName = extractProjectName(projectPath);
        
        return projectRepository.findOrCreate(projectName, projectPath);
    }
    
    /**
     * Extracts project name from absolute path.
     * Example: /Users/user/projects/my-app -> my-app
     */
    private String extractProjectName(String projectPath) {
        Path path = Paths.get(projectPath);
        String fileName = path.getFileName().toString();
        return Objects.nonNull(fileName) ? fileName : "unknown-project";
    }
    
    /**
     * Validates pattern usage request.
     */
    private void validateRequest(PatternUsageRequest request) {
        if (Objects.isNull(request)) {
            throw new IllegalArgumentException("Pattern usage request cannot be null");
        }
        if (Objects.isNull(request.getPatternId())) {
            throw new IllegalArgumentException("Pattern ID is required");
        }
        if (Objects.isNull(request.getProjectPath()) || request.getProjectPath().isBlank()) {
            throw new IllegalArgumentException("Project path is required");
        }
        if (Objects.isNull(request.getTaskType()) || request.getTaskType().isBlank()) {
            throw new IllegalArgumentException("Task type is required");
        }
        if (Objects.isNull(request.getSuccess())) {
            throw new IllegalArgumentException("Success flag is required");
        }
        if (Objects.nonNull(request.getCodeQualityScore())) {
            if (request.getCodeQualityScore() < 0.0 || request.getCodeQualityScore() > 1.0) {
                throw new IllegalArgumentException("Code quality score must be between 0.0 and 1.0");
            }
        }
    }
}

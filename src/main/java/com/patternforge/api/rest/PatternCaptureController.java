package com.patternforge.api.rest;

import com.patternforge.api.dto.ApiErrorResponse;
import com.patternforge.api.dto.PatternCaptureRequest;
import com.patternforge.api.dto.PatternCaptureResponse;
import com.patternforge.jooq.tables.records.ConversationalPatternsRecord;
import com.patternforge.jooq.tables.records.ProjectsRecord;
import com.patternforge.storage.repository.ConversationalPatternRepository;
import com.patternforge.storage.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.UUID;

/**
 * REST controller for capturing conversational patterns.
 * Handles pattern capture requests from user interactions.
 */
@RestController
@RequestMapping("/api/patterns")
@RequiredArgsConstructor
@Slf4j
public class PatternCaptureController {

    private final ConversationalPatternRepository conversationalPatternRepository;
    private final ProjectRepository projectRepository;

    /**
     * Capture a conversational pattern from user interaction.
     *
     * @param request Pattern capture request containing pattern details
     * @return {@link PatternCaptureResponse} on success, {@link ApiErrorResponse} on failure
     */
    @PostMapping("/capture")
    public ResponseEntity<?> capturePattern(@RequestBody PatternCaptureRequest request) {
        try {
            if (Objects.isNull(request.getDescription()) || request.getDescription().isBlank()) {
                return ResponseEntity.badRequest().body(ApiErrorResponse.of("Description is required"));
            }

            if (Objects.isNull(request.getProjectPath()) || request.getProjectPath().isBlank()) {
                return ResponseEntity.badRequest().body(ApiErrorResponse.of("Project path is required"));
            }

            if (Objects.isNull(request.getSource()) || request.getSource().isBlank()) {
                return ResponseEntity.badRequest().body(ApiErrorResponse.of("Source is required"));
            }

            if (!isValidSource(request.getSource())) {
                return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                    "Invalid source. Must be one of: user_explicit, user_correction, agent_observation"));
            }

            log.info("Capturing pattern - description: {}, source: {}, projectPath: {}",
                request.getDescription(), request.getSource(), request.getProjectPath());

            String projectName = extractProjectName(request.getProjectPath());
            ProjectsRecord project = projectRepository.findOrCreate(projectName, request.getProjectPath());

            ConversationalPatternsRecord patternRecord = createPatternRecord(request, project.getProjectId());
            ConversationalPatternsRecord savedPattern = conversationalPatternRepository.save(patternRecord);

            log.info("Pattern captured successfully - patternId: {}, projectId: {}",
                savedPattern.getId(), project.getProjectId());

            return ResponseEntity.ok(new PatternCaptureResponse(
                "success",
                savedPattern.getId(),
                project.getProjectId(),
                project.getProjectName(),
                "Pattern captured successfully"));

        } catch (IllegalArgumentException illegalArgumentException) {
            log.error("Validation error while capturing pattern", illegalArgumentException);
            return ResponseEntity.badRequest().body(ApiErrorResponse.of(illegalArgumentException.getMessage()));
        } catch (Exception exception) {
            log.error("Error capturing pattern", exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("Failed to capture pattern: " + exception.getMessage()));
        }
    }

    private ConversationalPatternsRecord createPatternRecord(PatternCaptureRequest request, UUID projectId) {
        ConversationalPatternsRecord record = new ConversationalPatternsRecord();

        record.setDescription(request.getDescription());
        record.setSource(request.getSource());
        record.setProjectId(projectId);

        if (Objects.nonNull(request.getCodeExample())) {
            record.setCodeExample(request.getCodeExample());
        }

        if (Objects.nonNull(request.getRationale())) {
            record.setRationale(request.getRationale());
        }

        if (Objects.nonNull(request.getConversationId())) {
            record.setConversationId(request.getConversationId());
        }

        record.setConfidence(Objects.nonNull(request.getConfidence()) ? request.getConfidence() : 0.95);
        record.setPromotionCount(0);
        record.setIsProjectStandard(false);
        record.setIsGlobalStandard(false);

        return record;
    }

    private String extractProjectName(String projectPath) {
        Path path = Paths.get(projectPath);
        Path fileName = path.getFileName();
        return Objects.nonNull(fileName) ? fileName.toString() : projectPath;
    }

    private boolean isValidSource(String source) {
        return "user_explicit".equals(source)
            || "user_correction".equals(source)
            || "agent_observation".equals(source);
    }
}

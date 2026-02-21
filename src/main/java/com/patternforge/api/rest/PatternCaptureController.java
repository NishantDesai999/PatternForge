package com.patternforge.api.rest;

import com.patternforge.api.dto.PatternCaptureRequest;
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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
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
     * @return Response with pattern ID, project ID, and status
     */
    @PostMapping("/capture")
    public ResponseEntity<Map<String, Object>> capturePattern(@RequestBody PatternCaptureRequest request) {
        try {
            // Validate required fields
            if (Objects.isNull(request.getDescription()) || request.getDescription().isBlank()) {
                return buildErrorResponse("Description is required", HttpStatus.BAD_REQUEST);
            }
            
            if (Objects.isNull(request.getProjectPath()) || request.getProjectPath().isBlank()) {
                return buildErrorResponse("Project path is required", HttpStatus.BAD_REQUEST);
            }
            
            if (Objects.isNull(request.getSource()) || request.getSource().isBlank()) {
                return buildErrorResponse("Source is required", HttpStatus.BAD_REQUEST);
            }
            
            // Validate source enum value
            if (!isValidSource(request.getSource())) {
                return buildErrorResponse(
                    "Invalid source. Must be one of: user_explicit, user_correction, agent_observation",
                    HttpStatus.BAD_REQUEST
                );
            }
            
            log.info("Capturing pattern - description: {}, source: {}, projectPath: {}", 
                request.getDescription(), request.getSource(), request.getProjectPath());
            
            // 1. Find or create project
            String projectName = extractProjectName(request.getProjectPath());
            ProjectsRecord project = projectRepository.findOrCreate(projectName, request.getProjectPath());
            
            // 2. Create conversational pattern record
            ConversationalPatternsRecord patternRecord = createPatternRecord(request, project.getProjectId());
            
            // 3. Save to database
            ConversationalPatternsRecord savedPattern = conversationalPatternRepository.save(patternRecord);
            
            log.info("Pattern captured successfully - patternId: {}, projectId: {}", 
                savedPattern.getId(), project.getProjectId());
            
            // 4. Build success response
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("pattern_id", savedPattern.getId());
            response.put("project_id", project.getProjectId());
            response.put("project_name", project.getProjectName());
            response.put("message", "Pattern captured successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException illegalArgumentException) {
            log.error("Validation error while capturing pattern", illegalArgumentException);
            return buildErrorResponse(illegalArgumentException.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception exception) {
            log.error("Error capturing pattern", exception);
            return buildErrorResponse(
                "Failed to capture pattern: " + exception.getMessage(), 
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
    
    /**
     * Create a conversational pattern record from the request.
     */
    private ConversationalPatternsRecord createPatternRecord(
            PatternCaptureRequest request, 
            UUID projectId) {
        
        ConversationalPatternsRecord record = new ConversationalPatternsRecord();
        
        // Set required fields
        record.setDescription(request.getDescription());
        record.setSource(request.getSource());
        record.setProjectId(projectId);
        
        // Set optional fields
        if (Objects.nonNull(request.getCodeExample())) {
            record.setCodeExample(request.getCodeExample());
        }
        
        if (Objects.nonNull(request.getRationale())) {
            record.setRationale(request.getRationale());
        }
        
        if (Objects.nonNull(request.getConversationId())) {
            record.setConversationId(request.getConversationId());
        }
        
        // Set confidence (default 0.95)
        Double confidence = Objects.nonNull(request.getConfidence()) 
            ? request.getConfidence() 
            : 0.95;
        record.setConfidence(confidence);
        
        // Set defaults
        record.setPromotionCount(0);
        record.setIsProjectStandard(false);
        record.setIsGlobalStandard(false);
        
        // Timestamps will be set by the repository save method
        
        return record;
    }
    
    /**
     * Extract project name from project path.
     * Uses the last directory name in the path.
     */
    private String extractProjectName(String projectPath) {
        Path path = Paths.get(projectPath);
        Path fileName = path.getFileName();
        
        if (Objects.nonNull(fileName)) {
            return fileName.toString();
        }
        
        // Fallback to full path if extraction fails
        return projectPath;
    }
    
    /**
     * Validate source enum value.
     */
    private boolean isValidSource(String source) {
        return "user_explicit".equals(source) 
            || "user_correction".equals(source) 
            || "agent_observation".equals(source);
    }
    
    /**
     * Build error response with status and message.
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        
        return ResponseEntity.status(status).body(response);
    }
}

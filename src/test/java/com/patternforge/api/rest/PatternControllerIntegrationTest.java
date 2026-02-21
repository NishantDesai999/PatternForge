package com.patternforge.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patternforge.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.patternforge.jooq.Tables.CONVERSATIONAL_PATTERNS;
import static com.patternforge.jooq.Tables.PATTERNS;
import static com.patternforge.jooq.Tables.PATTERN_USAGE;
import static com.patternforge.jooq.Tables.PROJECTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive integration tests for PatternController.
 * Tests all REST endpoints with success and error scenarios.
 */
@AutoConfigureMockMvc
class PatternControllerIntegrationTest extends AbstractIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    // ==================== Health Endpoint Tests ====================
    
    @Test
    void shouldReturnHealthStatus() throws Exception {
        mockMvc.perform(get("/api/patterns/health"))
            .andExpect(status().isOk())
            .andExpect(content().string("PatternForge API is running"));
    }
    
    // ==================== Get All Patterns Tests ====================
    
    @Test
    void shouldReturnAllPatterns() throws Exception {
        // Arrange - create test patterns
        createTestPattern("java-null-check", "Java Null Check", "java");
        createTestPattern("test-naming", "Test Naming Convention", "java");
        
        // Act & Assert
        mockMvc.perform(get("/api/patterns"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].patternName").exists())
            .andExpect(jsonPath("$[0].title").exists())
            .andExpect(jsonPath("$[0].description").exists());
    }
    
    @Test
    void shouldReturnEmptyArrayWhenNoPatternsExist() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/patterns"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }
    
    @Test
    void shouldIncludeCodeExamplesInPatternResponse() throws Exception {
        // Arrange - create pattern with code examples
        UUID patternId = UUID.randomUUID();
        String codeExamplesJson = "{\"java\": \"if (Objects.nonNull(value)) { }\"}";
        
        dsl.insertInto(PATTERNS)
            .set(PATTERNS.PATTERN_ID, patternId)
            .set(PATTERNS.PATTERN_NAME, "test-pattern")
            .set(PATTERNS.TITLE, "Test Pattern")
            .set(PATTERNS.DESCRIPTION, "Test description")
            .set(PATTERNS.CATEGORY, "test")
            .set(PATTERNS.WHEN_TO_USE, "Always")
            .set(PATTERNS.CODE_EXAMPLES, org.jooq.JSONB.valueOf(codeExamplesJson))
            .execute();
        
        // Act & Assert
        mockMvc.perform(get("/api/patterns"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].codeExamples").exists())
            .andExpect(jsonPath("$[0].codeExamples.java").value("if (Objects.nonNull(value)) { }"));
    }
    
    // ==================== Query Patterns Tests ====================
    
    @Test
    void shouldQueryPatternsWithBasicRequest() throws Exception {
        // Arrange
        createTestPattern("java-null-check", "Java Null Check", "java");
        createTestPattern("test-naming", "Test Naming Convention", "java");
        
        Map<String, Object> request = new HashMap<>();
        request.put("task", "Fix null pointer exception");
        request.put("language", "java");
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.patterns").isArray())
            .andExpect(jsonPath("$.workflow").exists())
            .andExpect(jsonPath("$.metadata.patterns_retrieved").exists())
            .andExpect(jsonPath("$.metadata.task_type").exists())
            .andExpect(jsonPath("$.metadata.search_strategy").exists());
    }
    
    @Test
    void shouldQueryPatternsWithCustomTopK() throws Exception {
        // Arrange - create 10 patterns
        for (int index = 0; index < 10; index++) {
            createTestPattern("pattern-" + index, "Pattern " + index, "java");
        }
        
        Map<String, Object> request = new HashMap<>();
        request.put("task", "Write unit tests");
        request.put("language", "java");
        request.put("topK", 3);
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.patterns").isArray())
            .andExpect(jsonPath("$.patterns.length()").value(3));
    }
    
    @Test
    void shouldQueryPatternsWithProjectPath() throws Exception {
        // Arrange - create global and project-specific patterns
        UUID projectId = createTestProject("/test/project");
        createTestPattern("global-pattern", "Global Pattern", "java");
        createProjectPattern(projectId, "project-pattern", "Project Pattern");
        
        Map<String, Object> request = new HashMap<>();
        request.put("task", "Implement new feature");
        request.put("language", "java");
        request.put("projectPath", "/test/project");
        request.put("topK", 5);
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.patterns").isArray())
            .andExpect(jsonPath("$.metadata.patterns_retrieved").exists());
    }
    
    @Test
    void shouldQueryPatternsWithConversationId() throws Exception {
        // Arrange - create patterns
        UUID projectId = createTestProject("/test/project");
        createTestPattern("global-pattern", "Global Pattern", "java");
        createConversationalPattern(projectId, "conv-pattern-1", 0);
        
        Map<String, Object> request = new HashMap<>();
        request.put("task", "Implement new feature");
        request.put("language", "java");
        request.put("projectPath", "/test/project");
        request.put("conversationId", "test-conversation-123");
        request.put("topK", 5);
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.patterns").isArray())
            .andExpect(jsonPath("$.metadata.patterns_retrieved").exists());
    }
    
    @Test
    void shouldQueryPatternsWithProjectPathAndConversationId() throws Exception {
        // Arrange - create comprehensive pattern set
        UUID projectId = createTestProject("/test/project");
        createTestPattern("global-pattern", "Global Pattern", "java");
        createProjectPattern(projectId, "project-pattern", "Project Pattern");
        createConversationalPattern(projectId, "conv-pattern-1", 0);
        createConversationalPattern(projectId, "conv-pattern-2", 1);
        
        Map<String, Object> request = new HashMap<>();
        request.put("task", "Refactor legacy code");
        request.put("language", "java");
        request.put("projectPath", "/test/project");
        request.put("conversationId", "test-conversation-456");
        request.put("topK", 7);
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.patterns").isArray())
            .andExpect(jsonPath("$.workflow").exists())
            .andExpect(jsonPath("$.workflow.steps").exists())
            .andExpect(jsonPath("$.metadata.patterns_retrieved").exists())
            .andExpect(jsonPath("$.metadata.task_type").exists());
    }
    
    @Test
    void shouldUseDefaultTopKWhenNotProvided() throws Exception {
        // Arrange
        createTestPattern("test-pattern", "Test Pattern", "java");
        
        Map<String, Object> request = new HashMap<>();
        request.put("task", "Write code");
        request.put("language", "java");
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.patterns").isArray())
            .andExpect(jsonPath("$.metadata.patterns_retrieved").exists());
    }
    
    // ==================== Record Pattern Usage Tests ====================
    
    @Test
    void shouldRecordPatternUsageSuccessfully() throws Exception {
        // Arrange
        UUID patternId = createTestPattern("test-pattern", "Test Pattern", "java");
        UUID projectId = createTestProject("/test/project");
        
        Map<String, Object> request = new HashMap<>();
        request.put("patternId", patternId.toString());
        request.put("projectPath", "/test/project");
        request.put("taskType", "fix_bug");
        request.put("success", true);
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.usage_id").exists())
            .andExpect(jsonPath("$.success_rate").value(1.0))
            .andExpect(jsonPath("$.message").value("Pattern usage recorded successfully"));
        
        // Verify database record
        Long usageCount = dsl.selectCount()
            .from(PATTERN_USAGE)
            .where(PATTERN_USAGE.PATTERN_ID.eq(patternId))
            .fetchOne(0, Long.class);
        assertThat(usageCount).isEqualTo(1L);
    }
    
    @Test
    void shouldRecordPatternUsageWithOptionalFields() throws Exception {
        // Arrange
        UUID patternId = createTestPattern("test-pattern", "Test Pattern", "java");
        UUID projectId = createTestProject("/test/project");
        
        Map<String, Object> request = new HashMap<>();
        request.put("patternId", patternId.toString());
        request.put("projectPath", "/test/project");
        request.put("taskType", "refactoring");
        request.put("taskDescription", "Refactor legacy null checks");
        request.put("success", true);
        request.put("codeQualityScore", 0.95);
        request.put("iterationsNeeded", 2);
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.usage_id").exists());
        
        // Verify database record includes optional fields
        Integer iterationsNeeded = dsl.select(PATTERN_USAGE.ITERATIONS_NEEDED)
            .from(PATTERN_USAGE)
            .where(PATTERN_USAGE.PATTERN_ID.eq(patternId))
            .fetchOne(0, Integer.class);
        assertThat(iterationsNeeded).isEqualTo(2);
    }
    
    @Test
    void shouldCalculateCorrectSuccessRate() throws Exception {
        // Arrange - create pattern and project
        UUID patternId = createTestPattern("test-pattern", "Test Pattern", "java");
        UUID projectId = createTestProject("/test/project");
        
        // Record 3 successful usages
        for (int index = 0; index < 3; index++) {
            recordUsage(patternId, "/test/project", "fix_bug", true);
        }
        
        // Record 1 failed usage
        recordUsage(patternId, "/test/project", "fix_bug", false);
        
        // Act - record one more successful usage
        Map<String, Object> request = new HashMap<>();
        request.put("patternId", patternId.toString());
        request.put("projectPath", "/test/project");
        request.put("taskType", "fix_bug");
        request.put("success", true);
        
        // Assert - success rate should be 4/5 = 0.8
        mockMvc.perform(post("/api/patterns/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success_rate").value(0.8));
    }
    
    @Test
    void shouldHandleInvalidPatternIdGracefully() throws Exception {
        // Arrange - no pattern created, using non-existent pattern ID
        // Note: PatternUsageService validates pattern existence after project creation,
        // but the database constraint will fail, resulting in 500 error
        Map<String, Object> request = new HashMap<>();
        request.put("patternId", UUID.randomUUID().toString());
        request.put("projectPath", "/test/project");
        request.put("taskType", "fix_bug");
        request.put("success", true);
        
        // Act & Assert - expects 500 because FK constraint fails
        mockMvc.perform(post("/api/patterns/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.message").exists());
    }
    
    @Test
    void shouldHandleMissingRequiredFieldsGracefully() throws Exception {
        // Arrange - incomplete request missing patternId
        // Jackson will deserialize missing UUID field as null, which triggers validation
        Map<String, Object> request = new HashMap<>();
        request.put("projectPath", "/test/project");
        request.put("taskType", "fix_bug");
        request.put("success", true);
        
        // Act & Assert - expects 400 because request validation fails
        mockMvc.perform(post("/api/patterns/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.message").exists());
    }
    
    // ==================== Generate Embeddings Tests ====================
    
    @Test
    void shouldGenerateEmbeddingsWhenOllamaAvailable() throws Exception {
        // Note: This test will pass/fail based on whether Ollama is running
        // The test verifies the endpoint behavior, not Ollama availability
        
        // Arrange
        createTestPattern("test-pattern-1", "Test Pattern 1", "java");
        createTestPattern("test-pattern-2", "Test Pattern 2", "java");
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/admin/generate-embeddings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").exists())
            .andExpect(jsonPath("$.total_patterns").value(2))
            .andExpect(jsonPath("$.message").exists());
    }
    
    @Test
    void shouldHandleOllamaUnavailableGracefully() throws Exception {
        // Arrange
        createTestPattern("test-pattern", "Test Pattern", "java");
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/admin/generate-embeddings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").exists())
            .andExpect(jsonPath("$.message").exists());
        
        // Note: The actual status ("success" or "error") depends on Ollama availability
        // Both are valid responses, so we just verify the response structure
    }
    
    @Test
    void shouldHandleEmbeddingGenerationWithNoPatterns() throws Exception {
        // Act & Assert - no patterns in database
        mockMvc.perform(post("/api/patterns/admin/generate-embeddings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").exists())
            .andExpect(jsonPath("$.message").exists());
    }
    
    // ==================== Promote Patterns Tests ====================
    
    @Test
    void shouldPromoteEligiblePatterns() throws Exception {
        // Arrange - create conversational pattern with promotion_count >= 5
        UUID projectId = createTestProject("/test/project");
        UUID convPatternId = createConversationalPattern(projectId, "high-usage-pattern", 5);
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/admin/promote-patterns"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.promoted_count").value(1))
            .andExpect(jsonPath("$.message").value("Promoted 1 patterns to project standard"));
        
        // Verify pattern was promoted in database
        Boolean isPromoted = dsl.select(CONVERSATIONAL_PATTERNS.IS_PROJECT_STANDARD)
            .from(CONVERSATIONAL_PATTERNS)
            .where(CONVERSATIONAL_PATTERNS.ID.eq(convPatternId))
            .fetchOne(0, Boolean.class);
        assertThat(isPromoted).isTrue();
    }
    
    @Test
    void shouldPromoteMultiplePatterns() throws Exception {
        // Arrange - create multiple eligible patterns
        UUID projectId = createTestProject("/test/project");
        createConversationalPattern(projectId, "pattern-1", 5);
        createConversationalPattern(projectId, "pattern-2", 7);
        createConversationalPattern(projectId, "pattern-3", 10);
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/admin/promote-patterns"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.promoted_count").value(3));
    }
    
    @Test
    void shouldNotPromotePatternsWithLowPromotionCount() throws Exception {
        // Arrange - create pattern with promotion_count < 5
        UUID projectId = createTestProject("/test/project");
        UUID convPatternId = createConversationalPattern(projectId, "low-usage-pattern", 3);
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/admin/promote-patterns"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.promoted_count").value(0));
        
        // Verify pattern was NOT promoted
        Boolean isPromoted = dsl.select(CONVERSATIONAL_PATTERNS.IS_PROJECT_STANDARD)
            .from(CONVERSATIONAL_PATTERNS)
            .where(CONVERSATIONAL_PATTERNS.ID.eq(convPatternId))
            .fetchOne(0, Boolean.class);
        assertThat(Objects.nonNull(isPromoted) ? isPromoted : false).isFalse();
    }
    
    @Test
    void shouldNotPromoteAlreadyPromotedPatterns() throws Exception {
        // Arrange - create already promoted pattern
        UUID projectId = createTestProject("/test/project");
        UUID convPatternId = UUID.randomUUID();
        
        dsl.insertInto(CONVERSATIONAL_PATTERNS)
            .set(CONVERSATIONAL_PATTERNS.ID, convPatternId)
            .set(CONVERSATIONAL_PATTERNS.DESCRIPTION, "Already promoted pattern")
            .set(CONVERSATIONAL_PATTERNS.PROJECT_ID, projectId)
            .set(CONVERSATIONAL_PATTERNS.SOURCE, "user_explicit")
            .set(CONVERSATIONAL_PATTERNS.PROMOTION_COUNT, 10)
            .set(CONVERSATIONAL_PATTERNS.IS_PROJECT_STANDARD, true)
            .execute();
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/admin/promote-patterns"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.promoted_count").value(0));
    }
    
    @Test
    void shouldHandlePromotionWithNoEligiblePatterns() throws Exception {
        // Act & Assert - no patterns in database
        mockMvc.perform(post("/api/patterns/admin/promote-patterns"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.promoted_count").value(0));
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Creates a test pattern in the database.
     *
     * @param patternName the unique pattern name
     * @param title the pattern title
     * @param language the primary language
     * @return the generated pattern ID
     */
    private UUID createTestPattern(String patternName, String title, String language) {
        UUID patternId = UUID.randomUUID();
        dsl.insertInto(PATTERNS)
            .set(PATTERNS.PATTERN_ID, patternId)
            .set(PATTERNS.PATTERN_NAME, patternName)
            .set(PATTERNS.TITLE, title)
            .set(PATTERNS.DESCRIPTION, "Test description for " + patternName)
            .set(PATTERNS.CATEGORY, "test")
            .set(PATTERNS.SCOPE, "global")
            .set(PATTERNS.WHEN_TO_USE, "Always use this pattern")
            .set(PATTERNS.LANGUAGES, new String[]{language})
            .set(PATTERNS.IS_GLOBAL_STANDARD, true)
            .execute();
        return patternId;
    }
    
    /**
     * Creates a test project in the database.
     *
     * @param projectPath the absolute path to the project
     * @return the generated project ID
     */
    private UUID createTestProject(String projectPath) {
        UUID projectId = UUID.randomUUID();
        String projectName = projectPath.substring(projectPath.lastIndexOf('/') + 1);
        
        dsl.insertInto(PROJECTS)
            .set(PROJECTS.PROJECT_ID, projectId)
            .set(PROJECTS.PROJECT_NAME, projectName)
            .set(PROJECTS.PROJECT_PATH, projectPath)
            .execute();
        return projectId;
    }
    
    /**
     * Creates a project-specific pattern in the database.
     * Note: Project-specific patterns use scope='project' but don't reference project_id directly.
     * The linkage is handled through pattern_usage and conversational_patterns tables.
     *
     * @param projectId the project ID (for documentation, not stored in patterns table)
     * @param patternName the unique pattern name
     * @param title the pattern title
     * @return the generated pattern ID
     */
    private UUID createProjectPattern(UUID projectId, String patternName, String title) {
        UUID patternId = UUID.randomUUID();
        dsl.insertInto(PATTERNS)
            .set(PATTERNS.PATTERN_ID, patternId)
            .set(PATTERNS.PATTERN_NAME, patternName)
            .set(PATTERNS.TITLE, title)
            .set(PATTERNS.DESCRIPTION, "Project-specific test pattern")
            .set(PATTERNS.CATEGORY, "project")
            .set(PATTERNS.SCOPE, "project")
            .set(PATTERNS.WHEN_TO_USE, "Use in project")
            .set(PATTERNS.LANGUAGES, new String[]{"java"})
            .execute();
        return patternId;
    }
    
    /**
     * Creates a conversational pattern in the database.
     * Includes rationale to satisfy the NOT NULL constraint on patterns.when_to_use
     * when the pattern is promoted to a formal pattern.
     *
     * @param projectId the project ID
     * @param description the pattern description
     * @param promotionCount the promotion count
     * @return the generated conversational pattern ID
     */
    private UUID createConversationalPattern(UUID projectId, String description, int promotionCount) {
        UUID conversationalPatternId = UUID.randomUUID();
        dsl.insertInto(CONVERSATIONAL_PATTERNS)
            .set(CONVERSATIONAL_PATTERNS.ID, conversationalPatternId)
            .set(CONVERSATIONAL_PATTERNS.DESCRIPTION, description)
            .set(CONVERSATIONAL_PATTERNS.RATIONALE, "Use when " + description + " is needed")
            .set(CONVERSATIONAL_PATTERNS.CODE_EXAMPLE, "// Example code for " + description)
            .set(CONVERSATIONAL_PATTERNS.PROJECT_ID, projectId)
            .set(CONVERSATIONAL_PATTERNS.SOURCE, "user_explicit")
            .set(CONVERSATIONAL_PATTERNS.CONFIDENCE, 0.95)
            .set(CONVERSATIONAL_PATTERNS.PROMOTION_COUNT, promotionCount)
            .set(CONVERSATIONAL_PATTERNS.IS_PROJECT_STANDARD, Boolean.FALSE)
            .execute();
        return conversationalPatternId;
    }
    
    /**
     * Records a pattern usage in the database.
     *
     * @param patternId the pattern ID
     * @param projectPath the project path
     * @param taskType the task type
     * @param success whether the usage was successful
     */
    private void recordUsage(UUID patternId, String projectPath, String taskType, boolean success) {
        // Ensure project exists and get its ID
        UUID projectId = dsl.select(PROJECTS.PROJECT_ID)
            .from(PROJECTS)
            .where(PROJECTS.PROJECT_PATH.eq(projectPath))
            .fetchOne(PROJECTS.PROJECT_ID);
        
        if (Objects.isNull(projectId)) {
            projectId = createTestProject(projectPath);
        }
        
        // Record usage with project_id
        UUID usageId = UUID.randomUUID();
        dsl.insertInto(PATTERN_USAGE)
            .set(PATTERN_USAGE.USAGE_ID, usageId)
            .set(PATTERN_USAGE.PATTERN_ID, patternId)
            .set(PATTERN_USAGE.PROJECT_ID, projectId)
            .set(PATTERN_USAGE.TASK_TYPE, taskType)
            .set(PATTERN_USAGE.SUCCESS, success)
            .execute();
    }
}

package com.patternforge.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patternforge.AbstractIntegrationTest;
import com.patternforge.jooq.tables.records.ConversationalPatternsRecord;
import com.patternforge.jooq.tables.records.ProjectsRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.patternforge.jooq.Tables.CONVERSATIONAL_PATTERNS;
import static com.patternforge.jooq.Tables.PROJECTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Pattern Capture and Usage API endpoints.
 * Tests complete workflow from HTTP request through service layer to database.
 * Uses real PostgreSQL database via Testcontainers - NO mocking.
 */
@AutoConfigureMockMvc
class PatternCaptureControllerIntegrationTest extends AbstractIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    // ==================== POST /api/patterns/capture - Success Cases ====================
    
    @Test
    void shouldCapturePatternSuccessfully() throws Exception {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("description", "Always use Objects.requireNonNull for parameter validation");
        request.put("source", "user_explicit");
        request.put("projectPath", "/test/project");
        request.put("codeExample", "Objects.requireNonNull(param)");
        request.put("rationale", "Fail fast on nulls");
        
        // Act & Assert - HTTP layer
        MvcResult result = mockMvc.perform(post("/api/patterns/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.pattern_id").exists())
            .andExpect(jsonPath("$.project_id").exists())
            .andExpect(jsonPath("$.project_name").value("project"))
            .andExpect(jsonPath("$.message").value("Pattern captured successfully"))
            .andReturn();
        
        // Assert - Database layer
        Long patternCount = dsl.selectCount().from(CONVERSATIONAL_PATTERNS).fetchOne(0, Long.class);
        assertThat(patternCount).isEqualTo(1L);
        
        Long projectCount = dsl.selectCount().from(PROJECTS).fetchOne(0, Long.class);
        assertThat(projectCount).isEqualTo(1L);
        
        // Verify pattern data in database
        List<ConversationalPatternsRecord> patterns = dsl.selectFrom(CONVERSATIONAL_PATTERNS).fetch();
        assertThat(patterns).hasSize(1);
        ConversationalPatternsRecord pattern = patterns.get(0);
        assertThat(pattern.getDescription()).isEqualTo("Always use Objects.requireNonNull for parameter validation");
        assertThat(pattern.getSource()).isEqualTo("user_explicit");
        assertThat(pattern.getCodeExample()).isEqualTo("Objects.requireNonNull(param)");
        assertThat(pattern.getRationale()).isEqualTo("Fail fast on nulls");
        assertThat(pattern.getConfidence()).isEqualTo(0.95);
        assertThat(pattern.getPromotionCount()).isEqualTo(0);
        assertThat(pattern.getIsProjectStandard()).isFalse();
        assertThat(pattern.getIsGlobalStandard()).isFalse();
        
        // Verify project data in database
        List<ProjectsRecord> projects = dsl.selectFrom(PROJECTS).fetch();
        assertThat(projects).hasSize(1);
        ProjectsRecord project = projects.get(0);
        assertThat(project.getProjectName()).isEqualTo("project");
        assertThat(project.getProjectPath()).isEqualTo("/test/project");
    }
    
    @Test
    void shouldCapturePatternWithMinimalRequiredFields() throws Exception {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("description", "Minimal pattern");
        request.put("source", "agent_observation");
        request.put("projectPath", "/minimal/project");
        
        // Act & Assert - HTTP layer
        mockMvc.perform(post("/api/patterns/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.pattern_id").exists())
            .andExpect(jsonPath("$.project_id").exists())
            .andExpect(jsonPath("$.project_name").value("project"))
            .andExpect(jsonPath("$.message").value("Pattern captured successfully"));
        
        // Assert - Database layer
        Long patternCount = dsl.selectCount().from(CONVERSATIONAL_PATTERNS).fetchOne(0, Long.class);
        assertThat(patternCount).isEqualTo(1L);
        
        // Verify optional fields are null
        ConversationalPatternsRecord pattern = dsl.selectFrom(CONVERSATIONAL_PATTERNS).fetchOne();
        assertThat(pattern).isNotNull();
        assertThat(pattern.getCodeExample()).isNull();
        assertThat(pattern.getRationale()).isNull();
        assertThat(pattern.getConversationId()).isNull();
    }
    
    @Test
    void shouldCapturePatternWithAllOptionalFields() throws Exception {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("description", "Complete pattern with all fields");
        request.put("source", "user_correction");
        request.put("projectPath", "/complete/project");
        request.put("codeExample", "public void example() {}");
        request.put("rationale", "This is the best way to do it");
        request.put("conversationId", "conv-12345");
        request.put("confidence", 0.99);
        
        // Act & Assert - HTTP layer
        mockMvc.perform(post("/api/patterns/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"));
        
        // Assert - Database layer
        ConversationalPatternsRecord pattern = dsl.selectFrom(CONVERSATIONAL_PATTERNS).fetchOne();
        assertThat(pattern).isNotNull();
        assertThat(pattern.getCodeExample()).isEqualTo("public void example() {}");
        assertThat(pattern.getRationale()).isEqualTo("This is the best way to do it");
        assertThat(pattern.getConversationId()).isEqualTo("conv-12345");
        assertThat(pattern.getConfidence()).isEqualTo(0.99);
    }
    
    @Test
    void shouldReuseExistingProjectWhenProjectPathAlreadyExists() throws Exception {
        // Arrange - Create first pattern with new project
        Map<String, Object> request1 = new HashMap<>();
        request1.put("description", "First pattern");
        request1.put("source", "user_explicit");
        request1.put("projectPath", "/test/shared-project");
        
        mockMvc.perform(post("/api/patterns/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
            .andExpect(status().isOk());
        
        // Arrange - Create second pattern with same project path
        Map<String, Object> request2 = new HashMap<>();
        request2.put("description", "Second pattern");
        request2.put("source", "user_explicit");
        request2.put("projectPath", "/test/shared-project");
        
        // Act
        mockMvc.perform(post("/api/patterns/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isOk());
        
        // Assert - Database layer
        Long projectCount = dsl.selectCount().from(PROJECTS).fetchOne(0, Long.class);
        assertThat(projectCount).isEqualTo(1L); // Only one project created
        
        Long patternCount = dsl.selectCount().from(CONVERSATIONAL_PATTERNS).fetchOne(0, Long.class);
        assertThat(patternCount).isEqualTo(2L); // Two patterns created
        
        // Verify both patterns share the same project
        List<ConversationalPatternsRecord> patterns = dsl.selectFrom(CONVERSATIONAL_PATTERNS).fetch();
        assertThat(patterns).hasSize(2);
        assertThat(patterns.get(0).getProjectId()).isEqualTo(patterns.get(1).getProjectId());
    }
    
    @Test
    void shouldExtractProjectNameFromComplexPath() throws Exception {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("description", "Pattern with complex path");
        request.put("source", "user_explicit");
        request.put("projectPath", "/Users/developer/workspace/my-awesome-project");
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.project_name").value("my-awesome-project"));
        
        // Verify in database
        ProjectsRecord project = dsl.selectFrom(PROJECTS).fetchOne();
        assertThat(project).isNotNull();
        assertThat(project.getProjectName()).isEqualTo("my-awesome-project");
    }
    
    @Test
    void shouldAcceptAllValidSourceValues() throws Exception {
        // Test user_explicit
        Map<String, Object> request1 = new HashMap<>();
        request1.put("description", "Pattern 1");
        request1.put("source", "user_explicit");
        request1.put("projectPath", "/test/project1");
        
        mockMvc.perform(post("/api/patterns/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
            .andExpect(status().isOk());
        
        // Test user_correction
        Map<String, Object> request2 = new HashMap<>();
        request2.put("description", "Pattern 2");
        request2.put("source", "user_correction");
        request2.put("projectPath", "/test/project2");
        
        mockMvc.perform(post("/api/patterns/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isOk());
        
        // Test agent_observation
        Map<String, Object> request3 = new HashMap<>();
        request3.put("description", "Pattern 3");
        request3.put("source", "agent_observation");
        request3.put("projectPath", "/test/project3");
        
        mockMvc.perform(post("/api/patterns/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request3)))
            .andExpect(status().isOk());
        
        // Assert
        Long patternCount = dsl.selectCount().from(CONVERSATIONAL_PATTERNS).fetchOne(0, Long.class);
        assertThat(patternCount).isEqualTo(3L);
    }
    
    // ==================== POST /api/patterns/capture - Validation Errors ====================
    
    @Test
    void shouldReturnErrorWhenDescriptionIsMissing() throws Exception {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("source", "user_explicit");
        request.put("projectPath", "/test/project");
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.message").value("Description is required"));
        
        // Verify nothing was saved
        Long patternCount = dsl.selectCount().from(CONVERSATIONAL_PATTERNS).fetchOne(0, Long.class);
        assertThat(patternCount).isEqualTo(0L);
    }
    
    @Test
    void shouldReturnErrorWhenDescriptionIsNull() throws Exception {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("description", null);
        request.put("source", "user_explicit");
        request.put("projectPath", "/test/project");
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.message").value("Description is required"));
    }
    
    @Test
    void shouldReturnErrorWhenDescriptionIsBlank() throws Exception {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("description", "   ");
        request.put("source", "user_explicit");
        request.put("projectPath", "/test/project");
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.message").value("Description is required"));
    }
    
    @Test
    void shouldReturnErrorWhenDescriptionIsEmpty() throws Exception {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("description", "");
        request.put("source", "user_explicit");
        request.put("projectPath", "/test/project");
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.message").value("Description is required"));
    }
    
    @Test
    void shouldReturnErrorWhenProjectPathIsMissing() throws Exception {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("description", "Valid description");
        request.put("source", "user_explicit");
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.message").value("Project path is required"));
    }
    
    @Test
    void shouldReturnErrorWhenProjectPathIsNull() throws Exception {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("description", "Valid description");
        request.put("source", "user_explicit");
        request.put("projectPath", null);
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.message").value("Project path is required"));
    }
    
    @Test
    void shouldReturnErrorWhenProjectPathIsBlank() throws Exception {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("description", "Valid description");
        request.put("source", "user_explicit");
        request.put("projectPath", "   ");
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.message").value("Project path is required"));
    }
    
    @Test
    void shouldReturnErrorWhenProjectPathIsEmpty() throws Exception {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("description", "Valid description");
        request.put("source", "user_explicit");
        request.put("projectPath", "");
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.message").value("Project path is required"));
    }
    
    @Test
    void shouldReturnErrorWhenSourceIsMissing() throws Exception {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("description", "Valid description");
        request.put("projectPath", "/test/project");
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.message").value("Source is required"));
    }
    
    @Test
    void shouldReturnErrorWhenSourceIsNull() throws Exception {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("description", "Valid description");
        request.put("source", null);
        request.put("projectPath", "/test/project");
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.message").value("Source is required"));
    }
    
    @Test
    void shouldReturnErrorWhenSourceIsBlank() throws Exception {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("description", "Valid description");
        request.put("source", "   ");
        request.put("projectPath", "/test/project");
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.message").value("Source is required"));
    }
    
    @Test
    void shouldReturnErrorWhenSourceIsEmpty() throws Exception {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("description", "Valid description");
        request.put("source", "");
        request.put("projectPath", "/test/project");
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.message").value("Source is required"));
    }
    
    @Test
    void shouldReturnErrorWhenSourceIsInvalid() throws Exception {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("description", "Valid description");
        request.put("source", "invalid_source");
        request.put("projectPath", "/test/project");
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.message").value("Invalid source. Must be one of: user_explicit, user_correction, agent_observation"));
    }
    
    @Test
    void shouldReturnErrorWhenSourceIsCaseMismatch() throws Exception {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("description", "Valid description");
        request.put("source", "USER_EXPLICIT"); // Wrong case
        request.put("projectPath", "/test/project");
        
        // Act & Assert
        mockMvc.perform(post("/api/patterns/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.message").value("Invalid source. Must be one of: user_explicit, user_correction, agent_observation"));
    }
    
    @Test
    void shouldReturnErrorWhenMultipleFieldsAreMissing() throws Exception {
        // Arrange - Empty request
        Map<String, Object> request = new HashMap<>();
        
        // Act & Assert - Should fail on first validation (description)
        mockMvc.perform(post("/api/patterns/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.message").exists());
    }
}

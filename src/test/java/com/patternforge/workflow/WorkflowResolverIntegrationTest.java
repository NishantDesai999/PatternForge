package com.patternforge.workflow;

import com.patternforge.AbstractIntegrationTest;
import com.patternforge.retrieval.model.RetrievedPattern;
import com.patternforge.workflow.model.WorkflowResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for WorkflowResolver — 3-level hierarchy resolution.
 */
class WorkflowResolverIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WorkflowResolver workflowResolver;

    @TempDir
    Path tempDir;

    private static final String SAMPLE_WORKFLOW = """
            ---
            workflow_name: test_fix_workflow
            ---
            ## Step 1: Read failing test
            **Tool**: file_reader
            **Target**: test_file
            **Validation**: File is readable
            """;

    // ==================== Fallback to generated ====================

    @Test
    void shouldFallBackToGeneratedWhenNoWorkflowFilesExist() {
        WorkflowResponse result = workflowResolver.resolveWorkflow(
                "fix_test",
                "fix the failing test",
                tempDir.toString(),
                List.of()
        );

        assertThat(result).isNotNull();
        assertThat(result.getSource()).contains("generated");
        assertThat(result.isUserDefined()).isFalse();
        assertThat(result.getSteps()).isNotEmpty();
    }

    @Test
    void shouldFallBackToGeneratedWhenProjectPathIsNull() {
        WorkflowResponse result = workflowResolver.resolveWorkflow(
                "add_endpoint",
                "add REST endpoint",
                null,
                List.of()
        );

        assertThat(result).isNotNull();
        assertThat(result.getSource()).contains("generated");
        assertThat(result.isUserDefined()).isFalse();
    }

    @Test
    void shouldFallBackToGeneratedWhenProjectPathIsBlank() {
        WorkflowResponse result = workflowResolver.resolveWorkflow(
                "refactor",
                "refactor the service",
                "   ",
                List.of()
        );

        assertThat(result).isNotNull();
        assertThat(result.getSource()).contains("generated");
    }

    // ==================== Project-specific workflow ====================

    @Test
    void shouldUseProjectWorkflowWhenWorkflowFileExists() throws IOException {
        // Arrange — create a .claude/workflows/fix_test.md in the temp project directory
        Path claudeDir = tempDir.resolve(".claude/workflows");
        Files.createDirectories(claudeDir);
        Files.writeString(claudeDir.resolve("fix_test.md"), SAMPLE_WORKFLOW);

        // Act
        WorkflowResponse result = workflowResolver.resolveWorkflow(
                "fix_test",
                "fix the failing test",
                tempDir.toString(),
                List.of()
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.isUserDefined()).isTrue();
        assertThat(result.getSource()).contains("project");
        assertThat(result.getSteps()).isNotEmpty();
    }

    @Test
    void shouldNotLoadProjectWorkflowForDifferentTaskType() throws IOException {
        // Arrange — only fix_test.md exists but we request add_endpoint
        Path claudeDir = tempDir.resolve(".claude/workflows");
        Files.createDirectories(claudeDir);
        Files.writeString(claudeDir.resolve("fix_test.md"), SAMPLE_WORKFLOW);

        // Act
        WorkflowResponse result = workflowResolver.resolveWorkflow(
                "add_endpoint",
                "add endpoint",
                tempDir.toString(),
                List.of()
        );

        // Assert — falls back to generated since add_endpoint.md doesn't exist
        assertThat(result.isUserDefined()).isFalse();
        assertThat(result.getSource()).contains("generated");
    }

    // ==================== Pattern enrichment ====================

    @Test
    void shouldEnrichGeneratedWorkflowWithPatterns() {
        RetrievedPattern pattern = RetrievedPattern.builder()
                .patternId(UUID.randomUUID().toString())
                .title("Test Pattern")
                .description("A test pattern")
                .build();

        WorkflowResponse result = workflowResolver.resolveWorkflow(
                "fix_test",
                "fix failing test",
                tempDir.toString(),
                List.of(pattern)
        );

        assertThat(result.getSteps()).isNotEmpty();
        // At least one step should have resolved patterns
        boolean hasResolvedPattern = result.getSteps().stream()
                .anyMatch(s -> s.getResolvedPatterns() != null && !s.getResolvedPatterns().isEmpty());
        assertThat(hasResolvedPattern).isTrue();
    }

    @Test
    void shouldReturnWorkflowEvenWithEmptyPatternList() {
        WorkflowResponse result = workflowResolver.resolveWorkflow(
                "fix_test",
                "fix test",
                tempDir.toString(),
                List.of()
        );

        assertThat(result).isNotNull();
        assertThat(result.getSteps()).isNotEmpty();
    }

    // ==================== Quality gates ====================

    @Test
    void shouldGenerateQualityGatesInWorkflow() {
        WorkflowResponse result = workflowResolver.resolveWorkflow(
                "add_endpoint",
                "add endpoint",
                tempDir.toString(),
                List.of()
        );

        assertThat(result.getQualityGates()).isNotEmpty();
        boolean hasCompilation = result.getQualityGates().stream()
                .anyMatch(g -> "compilation".equals(g.getGateName()));
        assertThat(hasCompilation).isTrue();
    }
}

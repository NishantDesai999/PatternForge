package com.patternforge.workflow;

import com.patternforge.retrieval.model.RetrievedPattern;
import com.patternforge.workflow.model.ExecutionStep;
import com.patternforge.workflow.model.QualityGate;
import com.patternforge.workflow.model.WorkflowResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for WorkflowBuilder — task-specific workflow step generation.
 */
class WorkflowBuilderTest {

    private WorkflowBuilder workflowBuilder;

    @BeforeEach
    void setUp() {
        workflowBuilder = new WorkflowBuilder();
    }

    // ==================== fix_test workflow ====================

    @Test
    void shouldGenerateFixTestWorkflow() {
        WorkflowResponse result = workflowBuilder.build(List.of(), "fix_test", "fix failing test");

        assertThat(result).isNotNull();
        assertThat(result.getSource()).isEqualTo("generated");
        assertThat(result.isUserDefined()).isFalse();
        assertThat(result.getSteps()).isNotEmpty();
    }

    @Test
    void shouldGenerateFixTestWorkflowWithCorrectStepCount() {
        WorkflowResponse result = workflowBuilder.build(List.of(), "fix_test", "fix test");

        // fix_test generates 5 steps
        assertThat(result.getSteps()).hasSize(5);
    }

    @Test
    void shouldGenerateFixTestFirstStepAsReadTestFile() {
        WorkflowResponse result = workflowBuilder.build(List.of(), "fix_test", "fix test");

        ExecutionStep firstStep = result.getSteps().get(0);
        assertThat(firstStep.getStep()).isEqualTo(1);
        assertThat(firstStep.getAction()).contains("Read test file");
    }

    @Test
    void shouldIncludeCodeReviewStepInFixTest() {
        WorkflowResponse result = workflowBuilder.build(List.of(), "fix_test", "fix test");

        boolean hasReviewStep = result.getSteps().stream()
                .anyMatch(s -> s.getAgent() != null && s.getAgent().contains("java-code-reviewer"));
        assertThat(hasReviewStep).isTrue();
    }

    @Test
    void shouldIncludeBuildCommandInFixTest() {
        WorkflowResponse result = workflowBuilder.build(List.of(), "fix_test", "fix test");

        boolean hasBuildStep = result.getSteps().stream()
                .anyMatch(s -> s.getCommand() != null && s.getCommand().contains("mvn"));
        assertThat(hasBuildStep).isTrue();
    }

    // ==================== add_endpoint workflow ====================

    @Test
    void shouldGenerateAddEndpointWorkflow() {
        WorkflowResponse result = workflowBuilder.build(List.of(), "add_endpoint", "add REST endpoint");

        assertThat(result.getSteps()).isNotEmpty();
        assertThat(result.getSource()).isEqualTo("generated");
    }

    @Test
    void shouldGenerateAddEndpointWorkflowWithCorrectStepCount() {
        WorkflowResponse result = workflowBuilder.build(List.of(), "add_endpoint", "add endpoint");

        // add_endpoint generates 6 steps
        assertThat(result.getSteps()).hasSize(6);
    }

    @Test
    void shouldIncludeTestStepInAddEndpoint() {
        WorkflowResponse result = workflowBuilder.build(List.of(), "add_endpoint", "add endpoint");

        boolean hasTestStep = result.getSteps().stream()
                .anyMatch(s -> s.getAction() != null && s.getAction().toLowerCase().contains("test"));
        assertThat(hasTestStep).isTrue();
    }

    @Test
    void shouldIncludeUnitTestsQualityGateForAddEndpoint() {
        WorkflowResponse result = workflowBuilder.build(List.of(), "add_endpoint", "add endpoint");

        boolean hasTestGate = result.getQualityGates().stream()
                .anyMatch(g -> "unit_tests".equals(g.getGateName()));
        assertThat(hasTestGate).isTrue();
    }

    // ==================== refactor workflow ====================

    @Test
    void shouldGenerateRefactorWorkflow() {
        WorkflowResponse result = workflowBuilder.build(List.of(), "refactor", "refactor service");

        assertThat(result.getSteps()).isNotEmpty();
        assertThat(result.getSource()).isEqualTo("generated");
    }

    @Test
    void shouldGenerateRefactorWorkflowWithCorrectStepCount() {
        WorkflowResponse result = workflowBuilder.build(List.of(), "refactor", "refactor service");

        // refactor generates 5 steps
        assertThat(result.getSteps()).hasSize(5);
    }

    @Test
    void shouldIncludeUnitTestsQualityGateForRefactor() {
        WorkflowResponse result = workflowBuilder.build(List.of(), "refactor", "refactor service");

        boolean hasTestGate = result.getQualityGates().stream()
                .anyMatch(g -> "unit_tests".equals(g.getGateName()));
        assertThat(hasTestGate).isTrue();
    }

    // ==================== Generic/unknown workflow ====================

    @Test
    void shouldGenerateGenericWorkflowForUnknownTaskType() {
        WorkflowResponse result = workflowBuilder.build(List.of(), "unknown_task", "do something");

        assertThat(result.getSteps()).isNotEmpty();
        assertThat(result.getSource()).isEqualTo("generated");
    }

    @Test
    void shouldGenerateGenericWorkflowWithThreeSteps() {
        WorkflowResponse result = workflowBuilder.build(List.of(), "unknown_task", "task");

        // Generic generates 3 steps
        assertThat(result.getSteps()).hasSize(3);
    }

    // ==================== Quality Gates ====================

    @Test
    void shouldAlwaysIncludeCompilationQualityGate() {
        for (String taskType : List.of("fix_test", "add_endpoint", "refactor", "unknown_task")) {
            WorkflowResponse result = workflowBuilder.build(List.of(), taskType, "task");

            boolean hasCompilation = result.getQualityGates().stream()
                    .anyMatch(g -> "compilation".equals(g.getGateName()));
            assertThat(hasCompilation)
                    .as("Expected compilation gate for taskType=" + taskType)
                    .isTrue();
        }
    }

    @Test
    void shouldAlwaysIncludeCodeReviewQualityGate() {
        WorkflowResponse result = workflowBuilder.build(List.of(), "fix_test", "task");

        boolean hasReview = result.getQualityGates().stream()
                .anyMatch(g -> "code_review".equals(g.getGateName()));
        assertThat(hasReview).isTrue();
    }

    @Test
    void shouldSetCompilationGateAsBlocking() {
        WorkflowResponse result = workflowBuilder.build(List.of(), "fix_test", "task");

        QualityGate compilationGate = result.getQualityGates().stream()
                .filter(g -> "compilation".equals(g.getGateName()))
                .findFirst()
                .orElseThrow();
        assertThat(compilationGate.isBlocking()).isTrue();
    }

    @Test
    void shouldNotIncludeUnitTestsGateForFixTest() {
        WorkflowResponse result = workflowBuilder.build(List.of(), "fix_test", "fix test");

        boolean hasTestGate = result.getQualityGates().stream()
                .anyMatch(g -> "unit_tests".equals(g.getGateName()));
        assertThat(hasTestGate).isFalse();
    }

    // ==================== Pattern References ====================

    @Test
    void shouldAttachPatternReferencesToFirstStep() {
        RetrievedPattern pattern = RetrievedPattern.builder()
                .patternId(UUID.randomUUID().toString())
                .title("Null Check Pattern")
                .build();

        WorkflowResponse result = workflowBuilder.build(List.of(pattern), "fix_test", "fix test");

        ExecutionStep firstStepWithRefs = result.getSteps().stream()
                .filter(s -> s.getPatternReferences() != null && !s.getPatternReferences().isEmpty())
                .findFirst()
                .orElse(null);
        assertThat(firstStepWithRefs).isNotNull();
        assertThat(firstStepWithRefs.getPatternReferences()).contains(pattern.getPatternId());
    }

    @Test
    void shouldHandleEmptyPatternList() {
        WorkflowResponse result = workflowBuilder.build(List.of(), "fix_test", "fix test");

        assertThat(result.getSteps()).isNotEmpty();
        // Steps that have pattern references should have empty lists
        result.getSteps().stream()
                .filter(s -> s.getPatternReferences() != null)
                .forEach(s -> assertThat(s.getPatternReferences()).isEmpty());
    }

    // ==================== Complexity Estimation ====================

    @Test
    void shouldEstimateLowComplexityForThreeOrFewerSteps() {
        // Generic workflow has 3 steps → "low"
        WorkflowResponse result = workflowBuilder.build(List.of(), "unknown_task", "task");
        assertThat(result.getEstimatedComplexity()).isEqualTo("low");
    }

    @Test
    void shouldEstimateMediumComplexityForFourToSixSteps() {
        // fix_test has 5 steps → "medium"
        WorkflowResponse result = workflowBuilder.build(List.of(), "fix_test", "fix test");
        assertThat(result.getEstimatedComplexity()).isEqualTo("medium");
    }

    @Test
    void shouldEstimateMediumComplexityForSixSteps() {
        // add_endpoint has 6 steps → "medium"
        WorkflowResponse result = workflowBuilder.build(List.of(), "add_endpoint", "add endpoint");
        assertThat(result.getEstimatedComplexity()).isEqualTo("medium");
    }

    // ==================== Validation ====================

    @Test
    void shouldThrowWhenTaskTypeIsNull() {
        assertThatThrownBy(() -> workflowBuilder.build(List.of(), null, "description"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskType cannot be null or blank");
    }

    @Test
    void shouldThrowWhenTaskTypeIsBlank() {
        assertThatThrownBy(() -> workflowBuilder.build(List.of(), "   ", "description"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskType cannot be null or blank");
    }

    @Test
    void shouldSetStepsInAscendingOrder() {
        WorkflowResponse result = workflowBuilder.build(List.of(), "fix_test", "fix test");

        List<ExecutionStep> steps = result.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            assertThat(steps.get(i).getStep()).isEqualTo(i + 1);
        }
    }
}

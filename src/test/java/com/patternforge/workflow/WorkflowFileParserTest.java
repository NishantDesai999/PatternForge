package com.patternforge.workflow;

import com.patternforge.workflow.model.ExecutionStep;
import com.patternforge.workflow.model.QualityGate;
import com.patternforge.workflow.model.WorkflowResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for WorkflowFileParser — YAML frontmatter + Markdown parsing.
 */
class WorkflowFileParserTest {

    private WorkflowFileParser parser;

    @BeforeEach
    void setUp() {
        parser = new WorkflowFileParser();
    }

    // ==================== Basic Parsing ====================

    @Test
    void shouldParseCompleteWorkflowFile() {
        String content = """
                ---
                workflow_name: fix_test
                version: "1.0"
                ---
                ## Step 1: Read the failing test
                **Action**: Read test file
                **Tool**: file_reader
                **Target**: test_file
                **Validation**: File is readable
                
                ## Step 2: Fix the import
                **Action**: Add missing import
                **Tool**: code_editor
                **Target**: test_file
                **Validation**: Import added
                """;

        WorkflowResponse result = parser.parse(content);

        assertThat(result).isNotNull();
        assertThat(result.getSource()).isEqualTo("fix_test");
        assertThat(result.isUserDefined()).isTrue();
        assertThat(result.getSteps()).hasSize(2);
    }

    @Test
    void shouldParseStepNumberAndAction() {
        String content = """
                ---
                workflow_name: my_workflow
                ---
                ## Step 1: Analyze code
                **Tool**: analyzer
                """;

        WorkflowResponse result = parser.parse(content);

        assertThat(result.getSteps()).hasSize(1);
        ExecutionStep step = result.getSteps().get(0);
        assertThat(step.getStep()).isEqualTo(1);
        assertThat(step.getAction()).isEqualTo("Analyze code");
    }

    @Test
    void shouldParseAllStepFields() {
        String content = """
                ---
                workflow_name: test_workflow
                ---
                ## Step 1: Do something
                **Action**: Perform the action
                **Tool**: my_tool
                **Target**: my_target
                **Validation**: Validation check
                **Command**: mvn clean test
                **Agent**: my-agent
                """;

        WorkflowResponse result = parser.parse(content);

        ExecutionStep step = result.getSteps().get(0);
        assertThat(step.getAction()).isEqualTo("Perform the action");
        assertThat(step.getTool()).isEqualTo("my_tool");
        assertThat(step.getTarget()).isEqualTo("my_target");
        assertThat(step.getValidation()).isEqualTo("Validation check");
        assertThat(step.getCommand()).isEqualTo("mvn clean test");
        assertThat(step.getAgent()).isEqualTo("my-agent");
    }

    @Test
    void shouldParseMultipleSteps() {
        String content = """
                ---
                workflow_name: multi_step
                ---
                ## Step 1: First action
                **Tool**: tool_one
                
                ## Step 2: Second action
                **Tool**: tool_two
                
                ## Step 3: Third action
                **Tool**: tool_three
                """;

        WorkflowResponse result = parser.parse(content);

        assertThat(result.getSteps()).hasSize(3);
        assertThat(result.getSteps().get(0).getStep()).isEqualTo(1);
        assertThat(result.getSteps().get(1).getStep()).isEqualTo(2);
        assertThat(result.getSteps().get(2).getStep()).isEqualTo(3);
    }

    // ==================== Frontmatter Handling ====================

    @Test
    void shouldUseFallbackSourceWhenWorkflowNameMissing() {
        String content = """
                ---
                version: "1.0"
                ---
                ## Step 1: Do something
                """;

        WorkflowResponse result = parser.parse(content);

        assertThat(result.getSource()).isEqualTo("unknown");
    }

    @Test
    void shouldTreatEntireContentAsMarkdownWhenNoFrontmatter() {
        String content = """
                ## Step 1: Do something
                **Tool**: my_tool
                """;

        WorkflowResponse result = parser.parse(content);

        assertThat(result).isNotNull();
        assertThat(result.getSteps()).hasSize(1);
        assertThat(result.getSource()).isEqualTo("unknown");
    }

    @Test
    void shouldHandleEmptyFrontmatter() {
        String content = """
                ---
                ---
                ## Step 1: Do something
                **Tool**: my_tool
                """;

        WorkflowResponse result = parser.parse(content);

        assertThat(result.getSteps()).hasSize(1);
        assertThat(result.getSource()).isEqualTo("unknown");
    }

    // ==================== Quality Gates ====================

    @Test
    void shouldParseQualityGates() {
        String content = """
                ---
                workflow_name: gated_workflow
                ---
                ## Step 1: Compile
                **Tool**: build_system
                
                ## Quality Gates
                
                ### Compilation Check
                **Type**: build
                **Command**: mvn compile
                **Blocking**: true
                **Description**: Must compile without errors
                """;

        WorkflowResponse result = parser.parse(content);

        assertThat(result.getQualityGates()).hasSize(1);
        QualityGate gate = result.getQualityGates().get(0);
        assertThat(gate.getGateName()).isEqualTo("Compilation Check");
        assertThat(gate.getGateType()).isEqualTo("build");
        assertThat(gate.getCommand()).isEqualTo("mvn compile");
        assertThat(gate.isBlocking()).isTrue();
        assertThat(gate.getDescription()).isEqualTo("Must compile without errors");
    }

    @Test
    void shouldParseMultipleQualityGates() {
        String content = """
                ---
                workflow_name: multi_gate
                ---
                ## Quality Gates
                
                ### Gate One
                **Type**: build
                
                ### Gate Two
                **Type**: test
                """;

        WorkflowResponse result = parser.parse(content);

        assertThat(result.getQualityGates()).hasSize(2);
        assertThat(result.getQualityGates().get(0).getGateName()).isEqualTo("Gate One");
        assertThat(result.getQualityGates().get(1).getGateName()).isEqualTo("Gate Two");
    }

    @Test
    void shouldReturnEmptyQualityGatesWhenNonePresent() {
        String content = """
                ---
                workflow_name: simple
                ---
                ## Step 1: Do something
                """;

        WorkflowResponse result = parser.parse(content);

        assertThat(result.getQualityGates()).isEmpty();
    }

    // ==================== Error Cases ====================

    @Test
    void shouldThrowWhenContentIsNull() {
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    @Test
    void shouldThrowWhenContentIsBlank() {
        assertThatThrownBy(() -> parser.parse("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    @Test
    void shouldThrowWhenContentIsEmpty() {
        assertThatThrownBy(() -> parser.parse(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    // ==================== Metadata ====================

    @Test
    void shouldSetUserDefinedTrue() {
        String content = """
                ---
                workflow_name: test
                ---
                ## Step 1: Action
                """;

        WorkflowResponse result = parser.parse(content);

        assertThat(result.isUserDefined()).isTrue();
    }

    @Test
    void shouldSetEstimatedComplexityToMedium() {
        String content = """
                ---
                workflow_name: test
                ---
                ## Step 1: Action
                """;

        WorkflowResponse result = parser.parse(content);

        assertThat(result.getEstimatedComplexity()).isEqualTo("medium");
    }

    @Test
    void shouldReturnEmptyStepsWhenMarkdownHasNoStepHeaders() {
        String content = """
                ---
                workflow_name: empty_steps
                ---
                Some general text without step headers.
                """;

        WorkflowResponse result = parser.parse(content);

        assertThat(result.getSteps()).isEmpty();
    }
}

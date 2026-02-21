package com.patternforge.workflow;

import com.patternforge.retrieval.model.RetrievedPattern;
import com.patternforge.workflow.model.ExecutionStep;
import com.patternforge.workflow.model.QualityGate;
import com.patternforge.workflow.model.WorkflowResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Generates workflow steps when no user-defined workflow exists.
 * Creates task-specific execution sequences based on retrieved patterns and task type.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowBuilder {

    /**
     * Builds a workflow response with execution steps and quality gates.
     *
     * @param patterns retrieved patterns from semantic search
     * @param taskType type of task (fix_test, add_endpoint, refactor)
     * @param taskDescription detailed description of the task
     * @return complete workflow response with steps and quality gates
     */
    public WorkflowResponse build(List<RetrievedPattern> patterns, String taskType, String taskDescription) {
        log.info("Building workflow for taskType={}, patternsCount={}", taskType, patterns.size());

        if (Objects.isNull(taskType) || taskType.isBlank()) {
            throw new IllegalArgumentException("taskType cannot be null or blank");
        }

        List<ExecutionStep> steps = generateStepsForTaskType(patterns, taskType, taskDescription);
        List<QualityGate> qualityGates = assignQualityGates(taskType, "java");

        return WorkflowResponse.builder()
                .source("generated")
                .userDefined(false)
                .steps(steps)
                .qualityGates(qualityGates)
                .estimatedComplexity(estimateComplexity(steps.size()))
                .build();
    }

    /**
     * Generates execution steps based on task type.
     *
     * @param patterns retrieved patterns
     * @param taskType type of task
     * @param taskDescription task description
     * @return list of execution steps
     */
    private List<ExecutionStep> generateStepsForTaskType(List<RetrievedPattern> patterns, String taskType, String taskDescription) {
        return switch (taskType) {
            case "fix_test" -> generateStepsForFixTest(patterns);
            case "add_endpoint" -> generateStepsForAddEndpoint(patterns);
            case "refactor" -> generateStepsForRefactor(patterns);
            default -> {
                log.warn("Unknown taskType={}, generating generic steps", taskType);
                yield generateGenericSteps(patterns);
            }
        };
    }

    /**
     * Generates steps for fixing failing tests.
     *
     * @param patterns retrieved patterns
     * @return list of execution steps for test fixing
     */
    private List<ExecutionStep> generateStepsForFixTest(List<RetrievedPattern> patterns) {
        List<ExecutionStep> steps = new ArrayList<>();

        steps.add(ExecutionStep.builder()
                .step(1)
                .action("Read test file")
                .tool("file_reader")
                .target("test_file")
                .validation("File exists and is readable")
                .build());

        steps.add(ExecutionStep.builder()
                .step(2)
                .action("Identify missing imports")
                .tool("code_analyzer")
                .target("test_file")
                .patternReferences(extractPatternIds(patterns))
                .resolvedPatterns(patterns)
                .validation("All missing imports identified")
                .build());

        steps.add(ExecutionStep.builder()
                .step(3)
                .action("Add imports in correct position")
                .tool("code_editor")
                .target("test_file")
                .validation("Imports added following project import order")
                .build());

        steps.add(ExecutionStep.builder()
                .step(4)
                .action("Invoke code reviewer")
                .agent("java-code-reviewer")
                .waitForUserApproval(true)
                .validation("Review passed with user approval")
                .build());

        steps.add(ExecutionStep.builder()
                .step(5)
                .action("Run mvn clean")
                .tool("build_system")
                .command("mvn clean compile")
                .validation("Build completes successfully")
                .build());

        return steps;
    }

    /**
     * Generates steps for adding REST API endpoint.
     *
     * @param patterns retrieved patterns
     * @return list of execution steps for endpoint creation
     */
    private List<ExecutionStep> generateStepsForAddEndpoint(List<RetrievedPattern> patterns) {
        List<ExecutionStep> steps = new ArrayList<>();

        steps.add(ExecutionStep.builder()
                .step(1)
                .action("Create controller method")
                .tool("code_generator")
                .target("controller_file")
                .patternReferences(extractPatternIds(patterns))
                .resolvedPatterns(patterns)
                .validation("Controller method created with proper annotations")
                .build());

        steps.add(ExecutionStep.builder()
                .step(2)
                .action("Create service layer method")
                .tool("code_generator")
                .target("service_file")
                .validation("Service method follows business logic patterns")
                .build());

        steps.add(ExecutionStep.builder()
                .step(3)
                .action("Create request/response DTOs")
                .tool("code_generator")
                .target("dto_files")
                .validation("DTOs created with validation annotations")
                .build());

        steps.add(ExecutionStep.builder()
                .step(4)
                .action("Add unit tests")
                .tool("test_generator")
                .target("test_files")
                .validation("Tests cover success and error scenarios")
                .build());

        steps.add(ExecutionStep.builder()
                .step(5)
                .action("Invoke code reviewer")
                .agent("java-code-reviewer")
                .waitForUserApproval(true)
                .validation("Review passed with user approval")
                .build());

        steps.add(ExecutionStep.builder()
                .step(6)
                .action("Run mvn clean")
                .tool("build_system")
                .command("mvn clean test")
                .validation("All tests pass")
                .build());

        return steps;
    }

    /**
     * Generates steps for refactoring code.
     *
     * @param patterns retrieved patterns
     * @return list of execution steps for refactoring
     */
    private List<ExecutionStep> generateStepsForRefactor(List<RetrievedPattern> patterns) {
        List<ExecutionStep> steps = new ArrayList<>();

        steps.add(ExecutionStep.builder()
                .step(1)
                .action("Analyze current implementation")
                .tool("code_analyzer")
                .target("target_files")
                .patternReferences(extractPatternIds(patterns))
                .resolvedPatterns(patterns)
                .validation("Code structure analyzed and refactoring plan created")
                .build());

        steps.add(ExecutionStep.builder()
                .step(2)
                .action("Apply refactoring patterns")
                .tool("code_editor")
                .target("target_files")
                .validation("Refactoring applied maintaining functionality")
                .build());

        steps.add(ExecutionStep.builder()
                .step(3)
                .action("Update tests if needed")
                .tool("test_editor")
                .target("test_files")
                .validation("Tests updated to match refactored code")
                .build());

        steps.add(ExecutionStep.builder()
                .step(4)
                .action("Invoke code reviewer")
                .agent("java-code-reviewer")
                .waitForUserApproval(true)
                .validation("Review passed with user approval")
                .build());

        steps.add(ExecutionStep.builder()
                .step(5)
                .action("Run mvn clean")
                .tool("build_system")
                .command("mvn clean test")
                .validation("All tests pass after refactoring")
                .build());

        return steps;
    }

    /**
     * Generates generic steps for unknown task types.
     *
     * @param patterns retrieved patterns
     * @return list of generic execution steps
     */
    private List<ExecutionStep> generateGenericSteps(List<RetrievedPattern> patterns) {
        List<ExecutionStep> steps = new ArrayList<>();

        steps.add(ExecutionStep.builder()
                .step(1)
                .action("Apply retrieved patterns")
                .tool("code_editor")
                .target("target_files")
                .patternReferences(extractPatternIds(patterns))
                .resolvedPatterns(patterns)
                .validation("Patterns applied successfully")
                .build());

        steps.add(ExecutionStep.builder()
                .step(2)
                .action("Invoke code reviewer")
                .agent("java-code-reviewer")
                .waitForUserApproval(true)
                .validation("Review passed with user approval")
                .build());

        steps.add(ExecutionStep.builder()
                .step(3)
                .action("Run mvn clean")
                .tool("build_system")
                .command("mvn clean compile")
                .validation("Build completes successfully")
                .build());

        return steps;
    }

    /**
     * Assigns quality gates based on task type and language.
     *
     * @param taskType type of task
     * @param language programming language
     * @return list of quality gates
     */
    private List<QualityGate> assignQualityGates(String taskType, String language) {
        List<QualityGate> gates = new ArrayList<>();

        gates.add(QualityGate.builder()
                .gateName("compilation")
                .gateType("build")
                .command("mvn clean compile")
                .isBlocking(true)
                .description("Code must compile without errors")
                .build());

        if ("add_endpoint".equals(taskType) || "refactor".equals(taskType)) {
            gates.add(QualityGate.builder()
                    .gateName("unit_tests")
                    .gateType("test")
                    .command("mvn test")
                    .isBlocking(true)
                    .description("All unit tests must pass")
                    .build());

            gates.add(QualityGate.builder()
                    .gateName("code_coverage")
                    .gateType("coverage")
                    .command("mvn verify")
                    .isBlocking(false)
                    .description("Code coverage meets project threshold")
                    .build());
        }

        if ("java".equals(language)) {
            gates.add(QualityGate.builder()
                    .gateName("checkstyle")
                    .gateType("style")
                    .command("mvn checkstyle:check")
                    .isBlocking(false)
                    .description("Code follows checkstyle rules")
                    .build());
        }

        gates.add(QualityGate.builder()
                .gateName("code_review")
                .gateType("review")
                .isBlocking(true)
                .description("Code review must be approved by user")
                .build());

        return gates;
    }

    /**
     * Extracts pattern IDs from retrieved patterns.
     *
     * @param patterns retrieved patterns
     * @return list of pattern IDs
     */
    private List<String> extractPatternIds(List<RetrievedPattern> patterns) {
        if (Objects.isNull(patterns)) {
            return new ArrayList<>();
        }
        return patterns.stream()
                .map(RetrievedPattern::getPatternId)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Estimates workflow complexity based on step count.
     *
     * @param stepCount number of execution steps
     * @return complexity estimate (low, medium, high)
     */
    private String estimateComplexity(int stepCount) {
        if (stepCount <= 3) {
            return "low";
        } else if (stepCount <= 6) {
            return "medium";
        } else {
            return "high";
        }
    }
}

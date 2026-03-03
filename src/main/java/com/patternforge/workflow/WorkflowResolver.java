package com.patternforge.workflow;

import com.patternforge.config.WorkflowProperties;
import com.patternforge.retrieval.model.RetrievedPattern;
import com.patternforge.workflow.model.ExecutionStep;
import com.patternforge.workflow.model.WorkflowResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves workflow definitions using hierarchical resolution strategy.
 * Checks project-specific workflows first (across all configured agent dirs),
 * then global workflows, finally falls back to PatternForge-generated workflows.
 * All workflows loaded fresh on-demand (no caching).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WorkflowResolver {

    private final WorkflowFileParser workflowFileParser;
    private final WorkflowBuilder workflowBuilder;
    private final WorkflowProperties workflowProperties;

    /**
     * Resolves workflow for given task using hierarchical resolution.
     * Resolution order: Project-specific > Global > PatternForge-generated
     *
     * @param taskType        task type identifier (e.g., "implement-feature", "fix-bug")
     * @param taskDescription detailed task description for workflow generation
     * @param projectPath     absolute path to project root
     * @param patterns        retrieved patterns to enrich workflow steps
     * @return resolved and enriched workflow response
     */
    public WorkflowResponse resolveWorkflow(
            String taskType,
            String taskDescription,
            String projectPath,
            List<RetrievedPattern> patterns) {

        log.debug("Resolving workflow for taskType={}, projectPath={}", taskType, projectPath);

        // Step 1: Check project-specific workflows (all configured dirs, first match wins)
        Optional<WorkflowResponse> projectWorkflow = loadProjectWorkflow(taskType, projectPath);
        if (projectWorkflow.isPresent()) {
            log.info("Using project-specific workflow for taskType={}", taskType);
            return enrichWithPatterns(projectWorkflow.get(), patterns);
        }

        // Step 2: Check global workflows (all configured paths, first match wins)
        Optional<WorkflowResponse> globalWorkflow = loadGlobalWorkflow(taskType);
        if (globalWorkflow.isPresent()) {
            log.info("Using global workflow for taskType={}", taskType);
            return enrichWithPatterns(globalWorkflow.get(), patterns);
        }

        // Step 3: Fall back to PatternForge-generated workflow
        log.info("No user-defined workflow found, generating PatternForge workflow for taskType={}", taskType);
        WorkflowResponse generatedWorkflow = workflowBuilder.build(
                patterns,
                taskType,
                taskDescription
        );
        generatedWorkflow.setSource("patternforge:generated");
        return generatedWorkflow;
    }

    /**
     * Loads project-specific workflow by iterating all configured project-relative dirs.
     * Checks dirs in order; the first readable .md file found wins.
     *
     * @param taskType    task type identifier
     * @param projectPath absolute path to project root
     * @return optional workflow response if a project workflow was found
     */
    private Optional<WorkflowResponse> loadProjectWorkflow(String taskType, String projectPath) {
        if (Objects.isNull(projectPath) || projectPath.isBlank()) {
            log.debug("Project path is null or empty, skipping project workflow lookup");
            return Optional.empty();
        }

        List<String> projectWorkflowDirs = workflowProperties.getProjectWorkflowDirs();
        if (Objects.isNull(projectWorkflowDirs) || projectWorkflowDirs.isEmpty()) {
            log.debug("No project workflow dirs configured, skipping project workflow lookup");
            return Optional.empty();
        }

        for (String dir : projectWorkflowDirs) {
            Path workflowPath = Paths.get(projectPath, dir, taskType + ".md");
            Optional<WorkflowResponse> result = tryLoadWorkflow(workflowPath, "project");
            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.empty();
    }

    /**
     * Loads global workflow by iterating all configured global workflow paths.
     * Checks paths in order; the first readable .md file found wins.
     *
     * @param taskType task type identifier
     * @return optional workflow response if a global workflow was found
     */
    private Optional<WorkflowResponse> loadGlobalWorkflow(String taskType) {
        List<String> globalWorkflowPaths = workflowProperties.getGlobalWorkflowPaths();
        if (Objects.isNull(globalWorkflowPaths) || globalWorkflowPaths.isEmpty()) {
            log.debug("No global workflow paths configured, skipping global workflow lookup");
            return Optional.empty();
        }

        for (String globalPath : globalWorkflowPaths) {
            if (Objects.isNull(globalPath) || globalPath.isBlank()) {
                continue;
            }
            Path workflowPath = Paths.get(globalPath, taskType + ".md");
            Optional<WorkflowResponse> result = tryLoadWorkflow(workflowPath, "global");
            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.empty();
    }

    /**
     * Attempts to load and parse a workflow file at the given path.
     * Returns empty if file does not exist, is unreadable, or fails to parse.
     *
     * @param workflowPath absolute path to the workflow .md file
     * @param sourcePrefix label prefix for the source field ("project" or "global")
     * @return optional workflow response if successfully loaded
     */
    private Optional<WorkflowResponse> tryLoadWorkflow(Path workflowPath, String sourcePrefix) {
        log.debug("Checking {} workflow at path={}", sourcePrefix, workflowPath);

        if (!Files.exists(workflowPath)) {
            log.debug("{} workflow not found at path={}", sourcePrefix, workflowPath);
            return Optional.empty();
        }

        if (!Files.isReadable(workflowPath)) {
            log.warn("{} workflow exists but is not readable at path={}", sourcePrefix, workflowPath);
            return Optional.empty();
        }

        try {
            String fileContent = Files.readString(workflowPath);
            WorkflowResponse workflow = workflowFileParser.parse(fileContent);
            workflow.setSource(sourcePrefix + ":" + workflowPath.toAbsolutePath().toString());
            workflow.setUserDefined(true);
            log.debug("Successfully loaded {} workflow from path={}", sourcePrefix, workflowPath);
            return Optional.of(workflow);
        } catch (Exception exception) {
            log.error("Failed to parse {} workflow at path={}", sourcePrefix, workflowPath, exception);
            return Optional.empty();
        }
    }

    /**
     * Enriches workflow steps with resolved pattern data.
     * Matches pattern references in steps to actual retrieved patterns.
     *
     * @param workflow base workflow to enrich
     * @param patterns list of retrieved patterns
     * @return enriched workflow with resolved patterns in steps
     */
    private WorkflowResponse enrichWithPatterns(
            WorkflowResponse workflow,
            List<RetrievedPattern> patterns) {

        if (Objects.isNull(patterns) || patterns.isEmpty()) {
            log.debug("No patterns provided for enrichment, returning workflow as-is");
            return workflow;
        }

        if (Objects.isNull(workflow.getSteps()) || workflow.getSteps().isEmpty()) {
            log.debug("Workflow has no steps to enrich, returning as-is");
            return workflow;
        }

        log.debug("Enriching workflow with {} patterns", patterns.size());

        for (ExecutionStep step : workflow.getSteps()) {
            if (Objects.isNull(step.getPatternReferences()) || step.getPatternReferences().isEmpty()) {
                continue;
            }

            List<String> resolvedPatternNames = new ArrayList<>();
            for (String patternReference : step.getPatternReferences()) {
                patterns.stream()
                        .filter(pattern -> matchesPatternReference(pattern, patternReference))
                        .findFirst()
                        .map(RetrievedPattern::getPatternName)
                        .ifPresent(resolvedPatternNames::add);
            }

            if (!resolvedPatternNames.isEmpty()) {
                step.setResolvedPatternNames(resolvedPatternNames);
                log.debug("Enriched step {} with {} resolved pattern names", step.getStep(), resolvedPatternNames.size());
            }
        }

        return workflow;
    }

    /**
     * Checks if retrieved pattern matches the given pattern reference.
     * Matches by pattern ID or title (case-insensitive).
     *
     * @param pattern   retrieved pattern
     * @param reference pattern reference string
     * @return true if pattern matches reference
     */
    private boolean matchesPatternReference(RetrievedPattern pattern, String reference) {
        if (Objects.isNull(reference) || reference.isBlank()) {
            return false;
        }

        if (Objects.nonNull(pattern.getPatternId()) && pattern.getPatternId().equalsIgnoreCase(reference)) {
            return true;
        }

        if (Objects.nonNull(pattern.getTitle()) && pattern.getTitle().equalsIgnoreCase(reference)) {
            return true;
        }

        return false;
    }
}

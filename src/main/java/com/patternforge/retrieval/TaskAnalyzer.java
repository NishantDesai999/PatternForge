package com.patternforge.retrieval;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.patternforge.retrieval.model.TaskContext;

import lombok.extern.slf4j.Slf4j;

/**
 * Analyzes task descriptions using heuristics to extract context.
 * MVP implementation uses simple keyword matching to identify task type, components, and concerns.
 */
@Service
@Slf4j
public class TaskAnalyzer {
    
    /**
     * Analyzes task description to build TaskContext using heuristic rules.
     * Extracts task type, components, and concerns from description text.
     *
     * @param taskDescription Natural language description of the task
     * @param language Programming language for the task
     * @param framework Framework being used (may be null)
     * @return TaskContext with extracted metadata
     */
    public TaskContext analyze(String taskDescription, String language, String framework) {
        if (Objects.isNull(taskDescription) || taskDescription.isBlank()) {
            log.warn("Task description is null or empty");
            return buildEmptyContext(language, framework);
        }
        
        String normalizedDescription = taskDescription.toLowerCase();
        log.debug("Analyzing task: {}", taskDescription);
        
        String taskType = inferTaskType(normalizedDescription);
        List<String> components = extractComponents(normalizedDescription);
        List<String> concerns = extractConcerns(normalizedDescription);
        
        TaskContext taskContext = TaskContext.builder()
            .description(taskDescription)
            .taskType(taskType)
            .components(components)
            .concerns(concerns)
            .language(language)
            .framework(framework)
            .build();
        
        log.info("Analyzed task - Type: {}, Components: {}, Concerns: {}", 
                 taskType, components, concerns);
        
        return taskContext;
    }
    
    /**
     * Infers task type from description using keyword matching.
     * MVP implementation supports: fix_test, add_endpoint, refactor.
     *
     * @param normalizedDescription Lowercase task description
     * @return Inferred task type or "general" if no match
     */
    private String inferTaskType(String normalizedDescription) {
        if (normalizedDescription.contains("test")) {
            return "fix_test";
        }
        
        if (normalizedDescription.contains("endpoint") || normalizedDescription.contains("controller")) {
            return "add_endpoint";
        }
        
        if (normalizedDescription.contains("refactor")) {
            return "refactor";
        }
        
        if (normalizedDescription.contains("add") || normalizedDescription.contains("create") 
            || normalizedDescription.contains("implement")) {
            return "add_feature";
        }
        
        if (normalizedDescription.contains("fix") || normalizedDescription.contains("bug")) {
            return "fix_bug";
        }
        
        return "general";
    }
    
    /**
     * Extracts component types mentioned in task description.
     * MVP implementation identifies: test, controller, service, repository, model.
     *
     * @param normalizedDescription Lowercase task description
     * @return List of identified components
     */
    private List<String> extractComponents(String normalizedDescription) {
        List<String> components = new ArrayList<>();
        
        if (normalizedDescription.contains("test")) {
            components.add("test");
        }
        
        if (normalizedDescription.contains("controller") || normalizedDescription.contains("endpoint")) {
            components.add("controller");
        }
        
        if (normalizedDescription.contains("service")) {
            components.add("service");
        }
        
        if (normalizedDescription.contains("repository") || normalizedDescription.contains("dao")) {
            components.add("repository");
        }
        
        if (normalizedDescription.contains("model") || normalizedDescription.contains("entity") 
            || normalizedDescription.contains("dto")) {
            components.add("model");
        }
        
        return components;
    }
    
    /**
     * Extracts technical concerns from task description.
     * MVP implementation identifies: imports, validation, error_handling, logging.
     *
     * @param normalizedDescription Lowercase task description
     * @return List of identified concerns
     */
    private List<String> extractConcerns(String normalizedDescription) {
        List<String> concerns = new ArrayList<>();
        
        if (normalizedDescription.contains("import")) {
            concerns.add("imports");
        }
        
        if (normalizedDescription.contains("validation") || normalizedDescription.contains("validate")) {
            concerns.add("validation");
        }
        
        if (normalizedDescription.contains("error") || normalizedDescription.contains("exception")) {
            concerns.add("error_handling");
        }
        
        if (normalizedDescription.contains("log")) {
            concerns.add("logging");
        }
        
        if (normalizedDescription.contains("security") || normalizedDescription.contains("authentication")) {
            concerns.add("security");
        }
        
        if (normalizedDescription.contains("performance") || normalizedDescription.contains("optimize")) {
            concerns.add("performance");
        }
        
        return concerns;
    }
    
    /**
     * Builds empty TaskContext when description is invalid.
     *
     * @param language Programming language
     * @param framework Framework name
     * @return Empty TaskContext with general type
     */
    private TaskContext buildEmptyContext(String language, String framework) {
        return TaskContext.builder()
            .description("")
            .taskType("general")
            .components(List.of())
            .concerns(List.of())
            .language(language)
            .framework(framework)
            .build();
    }
}

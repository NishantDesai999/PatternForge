package com.patternforge.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.patternforge.workflow.model.ExecutionStep;
import com.patternforge.workflow.model.QualityGate;
import com.patternforge.workflow.model.WorkflowResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses user-defined workflow files containing YAML frontmatter and Markdown content.
 * Extracts workflow metadata, execution steps, and quality gates.
 */
@Slf4j
@Service
public class WorkflowFileParser {

    private static final String FRONTMATTER_DELIMITER = "---";
    private static final Pattern STEP_HEADER_PATTERN = Pattern.compile("##\\s+Step\\s+(\\d+):\\s+(.+)");
    private static final Pattern ACTION_PATTERN = Pattern.compile("\\*\\*Action\\*\\*:\\s+(.+)");
    private static final Pattern TOOL_PATTERN = Pattern.compile("\\*\\*Tool\\*\\*:\\s+(.+)");
    private static final Pattern TARGET_PATTERN = Pattern.compile("\\*\\*Target\\*\\*:\\s+(.+)");
    private static final Pattern VALIDATION_PATTERN = Pattern.compile("\\*\\*Validation\\*\\*:\\s+(.+)");
    private static final Pattern COMMAND_PATTERN = Pattern.compile("\\*\\*Command\\*\\*:\\s+(.+)");
    private static final Pattern AGENT_PATTERN = Pattern.compile("\\*\\*Agent\\*\\*:\\s+(.+)");
    private static final Pattern QUALITY_GATE_SECTION = Pattern.compile("##\\s+Quality Gates", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUALITY_GATE_ITEM = Pattern.compile("\\*\\*(.+?)\\*\\*:\\s+(.+)");

    private final ObjectMapper yamlMapper;

    public WorkflowFileParser() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * Main entry point for parsing workflow file content.
     *
     * @param fileContent Complete workflow file content with YAML frontmatter and Markdown
     * @return Parsed workflow response with steps and quality gates
     */
    public WorkflowResponse parse(String fileContent) {
        if (Objects.isNull(fileContent) || fileContent.isBlank()) {
            log.error("File content is null or empty");
            throw new IllegalArgumentException("File content cannot be null or empty");
        }

        try {
            String[] parts = splitFrontmatter(fileContent);
            String yamlContent = parts[0];
            String markdownContent = parts[1];

            Map<String, Object> frontmatter = parseFrontmatter(yamlContent);
            List<ExecutionStep> steps = parseSteps(markdownContent);
            List<QualityGate> qualityGates = parseQualityGates(markdownContent);

            String source = (String) frontmatter.getOrDefault("workflow_name", "unknown");

            return WorkflowResponse.builder()
                    .source(source)
                    .userDefined(true)
                    .steps(steps)
                    .qualityGates(qualityGates)
                    .estimatedComplexity("medium")
                    .build();

        } catch (Exception exception) {
            log.error("Failed to parse workflow file", exception);
            throw new RuntimeException("Workflow parsing failed: " + exception.getMessage(), exception);
        }
    }

    /**
     * Splits content into YAML frontmatter and Markdown sections.
     *
     * @param content Complete file content
     * @return Array with [yamlContent, markdownContent]
     */
    private String[] splitFrontmatter(String content) {
        String trimmedContent = content.trim();
        
        if (!trimmedContent.startsWith(FRONTMATTER_DELIMITER)) {
            log.warn("No frontmatter found, treating entire content as Markdown");
            return new String[]{"", trimmedContent};
        }

        int firstDelimiterEnd = trimmedContent.indexOf('\n', FRONTMATTER_DELIMITER.length());
        if (firstDelimiterEnd == -1) {
            return new String[]{"", trimmedContent};
        }

        int secondDelimiterStart = trimmedContent.indexOf(FRONTMATTER_DELIMITER, firstDelimiterEnd);
        if (secondDelimiterStart == -1) {
            log.warn("Frontmatter not properly closed, treating entire content as Markdown");
            return new String[]{"", trimmedContent};
        }

        String yamlContent = trimmedContent.substring(firstDelimiterEnd + 1, secondDelimiterStart).trim();
        String markdownContent = trimmedContent.substring(secondDelimiterStart + FRONTMATTER_DELIMITER.length()).trim();

        return new String[]{yamlContent, markdownContent};
    }

    /**
     * Parses YAML frontmatter into a map.
     *
     * @param yamlContent YAML content string
     * @return Parsed frontmatter as map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFrontmatter(String yamlContent) {
        try {
            if (yamlContent.isBlank()) {
                return Map.of();
            }
            return yamlMapper.readValue(yamlContent, Map.class);
        } catch (Exception exception) {
            log.error("Failed to parse YAML frontmatter", exception);
            return Map.of();
        }
    }

    /**
     * Extracts execution steps from Markdown content.
     *
     * @param markdown Markdown content containing step definitions
     * @return List of parsed execution steps
     */
    private List<ExecutionStep> parseSteps(String markdown) {
        List<ExecutionStep> steps = new ArrayList<>();
        String[] lines = markdown.split("\n");
        
        ExecutionStep.ExecutionStepBuilder currentStepBuilder = null;
        int currentStepNumber = 0;

        for (String line : lines) {
            Matcher stepHeaderMatcher = STEP_HEADER_PATTERN.matcher(line);
            
            if (stepHeaderMatcher.find()) {
                if (Objects.nonNull(currentStepBuilder)) {
                    steps.add(currentStepBuilder.build());
                }
                
                currentStepNumber = Integer.parseInt(stepHeaderMatcher.group(1));
                String action = stepHeaderMatcher.group(2).trim();
                
                currentStepBuilder = ExecutionStep.builder()
                        .step(currentStepNumber)
                        .action(action)
                        .waitForUserApproval(false);
                continue;
            }

            if (Objects.isNull(currentStepBuilder)) {
                continue;
            }

            extractStepField(line, currentStepBuilder);
        }

        if (Objects.nonNull(currentStepBuilder)) {
            steps.add(currentStepBuilder.build());
        }

        log.debug("Parsed {} execution steps", steps.size());
        return steps;
    }

    /**
     * Extracts individual step field from a line and updates builder.
     *
     * @param line Current line being processed
     * @param stepBuilder Builder for current step
     */
    private void extractStepField(String line, ExecutionStep.ExecutionStepBuilder stepBuilder) {
        Matcher actionMatcher = ACTION_PATTERN.matcher(line);
        if (actionMatcher.find()) {
            stepBuilder.action(actionMatcher.group(1).trim());
            return;
        }

        Matcher toolMatcher = TOOL_PATTERN.matcher(line);
        if (toolMatcher.find()) {
            stepBuilder.tool(toolMatcher.group(1).trim());
            return;
        }

        Matcher targetMatcher = TARGET_PATTERN.matcher(line);
        if (targetMatcher.find()) {
            stepBuilder.target(targetMatcher.group(1).trim());
            return;
        }

        Matcher validationMatcher = VALIDATION_PATTERN.matcher(line);
        if (validationMatcher.find()) {
            stepBuilder.validation(validationMatcher.group(1).trim());
            return;
        }

        Matcher commandMatcher = COMMAND_PATTERN.matcher(line);
        if (commandMatcher.find()) {
            stepBuilder.command(commandMatcher.group(1).trim());
            return;
        }

        Matcher agentMatcher = AGENT_PATTERN.matcher(line);
        if (agentMatcher.find()) {
            stepBuilder.agent(agentMatcher.group(1).trim());
        }
    }

    /**
     * Extracts quality gates from Markdown content.
     *
     * @param markdown Markdown content containing quality gate definitions
     * @return List of parsed quality gates
     */
    private List<QualityGate> parseQualityGates(String markdown) {
        List<QualityGate> qualityGates = new ArrayList<>();
        String[] lines = markdown.split("\n");
        
        boolean inQualityGateSection = false;
        QualityGate.QualityGateBuilder currentGateBuilder = null;

        for (String line : lines) {
            if (QUALITY_GATE_SECTION.matcher(line).find()) {
                inQualityGateSection = true;
                continue;
            }

            if (!inQualityGateSection) {
                continue;
            }

            if (line.trim().startsWith("###")) {
                if (Objects.nonNull(currentGateBuilder)) {
                    qualityGates.add(currentGateBuilder.build());
                }
                
                String gateName = line.replaceFirst("###\\s+", "").trim();
                currentGateBuilder = QualityGate.builder()
                        .gateName(gateName)
                        .isBlocking(true);
                continue;
            }

            if (Objects.isNull(currentGateBuilder)) {
                continue;
            }

            Matcher gateFieldMatcher = QUALITY_GATE_ITEM.matcher(line);
            if (gateFieldMatcher.find()) {
                String fieldName = gateFieldMatcher.group(1).trim();
                String fieldValue = gateFieldMatcher.group(2).trim();
                
                switch (fieldName.toLowerCase()) {
                    case "type":
                        currentGateBuilder.gateType(fieldValue);
                        break;
                    case "command":
                        currentGateBuilder.command(fieldValue);
                        break;
                    case "blocking":
                        currentGateBuilder.isBlocking(Boolean.parseBoolean(fieldValue));
                        break;
                    case "description":
                        currentGateBuilder.description(fieldValue);
                        break;
                    default:
                        log.debug("Unknown quality gate field: {}", fieldName);
                }
            }
        }

        if (Objects.nonNull(currentGateBuilder)) {
            qualityGates.add(currentGateBuilder.build());
        }

        log.debug("Parsed {} quality gates", qualityGates.size());
        return qualityGates;
    }
}

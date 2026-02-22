package com.patternforge.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patternforge.api.dto.PatternCaptureRequest;
import com.patternforge.api.dto.PatternCaptureResponse;
import com.patternforge.api.dto.PatternQueryRequest;
import com.patternforge.api.dto.PatternQueryResponse;
import com.patternforge.api.dto.PatternUsageRequest;
import com.patternforge.api.dto.PatternUsageResponse;
import com.patternforge.api.rest.PatternCaptureController;
import com.patternforge.api.rest.PatternController;
import com.patternforge.api.rest.StandardsController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Handles MCP tool calls by delegating to existing REST controllers.
 * Translates MCP tool arguments into typed request DTOs and formats
 * typed response DTOs as MCP content blocks.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpToolHandler {

    private final PatternController patternController;
    private final PatternCaptureController patternCaptureController;
    private final StandardsController standardsController;
    private final ObjectMapper objectMapper;

    /**
     * Dispatches a tool call to the appropriate handler.
     *
     * @param toolName  name of the MCP tool to invoke
     * @param arguments tool arguments from the MCP request
     * @return MCP content block array as JsonNode
     */
    public JsonNode callTool(String toolName, JsonNode arguments) {
        try {
            return switch (toolName) {
                case "query_patterns"  -> handleQueryPatterns(arguments);
                case "capture_pattern" -> handleCapturePattern(arguments);
                case "get_standards"   -> handleGetStandards(arguments);
                case "record_usage"    -> handleRecordUsage(arguments);
                default -> errorContent("Unknown tool: " + toolName);
            };
        } catch (Exception exception) {
            log.error("Error executing MCP tool: {}", toolName, exception);
            return errorContent("Tool execution failed: " + exception.getMessage());
        }
    }

    /**
     * Returns the JSON Schema definitions for all available tools.
     * Used by tools/list to advertise capabilities.
     */
    public JsonNode getToolDefinitions() {
        ArrayNode tools = objectMapper.createArrayNode();

        tools.add(buildToolDef(
            "query_patterns",
            "Query PatternForge for coding patterns and a step-by-step workflow relevant to your current task. "
                + "Call this at the start of any coding task to get the patterns and workflow you should follow. "
                + "Returns patterns with code examples plus a workflow with ordered steps.",
            Map.of(
                "task",           new ToolParameter("string",  "Plain-English description of what you are doing. Be specific."),
                "language",       new ToolParameter("string",  "Programming language (java, python, typescript, go, etc.)"),
                "projectPath",    new ToolParameter("string",  "Absolute path to the project root. Include this for project-specific patterns."),
                "topK",           new ToolParameter("integer", "Number of patterns to return. Default 10. Use 5 for simple tasks, 15 for complex."),
                "conversationId", new ToolParameter("string",  "Optional session ID to link queries across a session for analytics.")
            ),
            new String[]{"task", "language"}
        ));

        tools.add(buildToolDef(
            "capture_pattern",
            "Capture a coding pattern or standard that was observed or taught during the current session. "
                + "Call this when: (1) the user says 'always do X' or 'from now on use Y', "
                + "(2) you just corrected an approach and want to remember it, "
                + "(3) you observed a recurring pattern worth storing. "
                + "Captured patterns auto-promote to project standards when reinforced 3+ times.",
            Map.of(
                "description",   new ToolParameter("string", "Clear description of the pattern or standard to capture"),
                "source",        new ToolParameter("string", "Where the pattern came from: user_explicit, user_correction, or agent_observation"),
                "projectPath",   new ToolParameter("string", "Absolute path to the project root"),
                "codeExample",   new ToolParameter("string", "Optional code example demonstrating the pattern"),
                "rationale",     new ToolParameter("string", "Why this pattern should be followed"),
                "conversationId",new ToolParameter("string", "Session ID to group patterns from the same conversation")
            ),
            new String[]{"description", "source", "projectPath"}
        ));

        tools.add(buildToolDef(
            "get_standards",
            "Get all coding standards and patterns for a project as a formatted document. "
                + "Use this instead of reading AGENTS.md or CLAUDE.md — PatternForge returns the same "
                + "information dynamically, always current. Returns global standards plus project-specific "
                + "patterns in a format ready to use as agent instructions.",
            Map.of(
                "projectPath", new ToolParameter("string", "Absolute path to the project root"),
                "language",    new ToolParameter("string", "Primary language of the project (java, python, typescript, go, etc.)")
            ),
            new String[]{"projectPath", "language"}
        ));

        tools.add(buildToolDef(
            "record_usage",
            "Record whether a pattern was successfully applied. "
                + "Call this after completing a task to help PatternForge learn which patterns work. "
                + "Successful patterns get higher relevance scores; failed ones get lower scores.",
            Map.of(
                "patternId",   new ToolParameter("string",  "UUID of the pattern that was used"),
                "projectPath", new ToolParameter("string",  "Absolute path to the project root"),
                "taskType",    new ToolParameter("string",  "Type of task: fix_bug, add_endpoint, implement_feature, etc."),
                "success",     new ToolParameter("boolean", "Whether the pattern was applied successfully")
            ),
            new String[]{"patternId", "projectPath", "success"}
        ));

        return tools;
    }

    // ─────────────────────────────────────────────────────────────
    // Tool handlers
    // ─────────────────────────────────────────────────────────────

    private JsonNode handleQueryPatterns(JsonNode args) throws Exception {
        PatternQueryRequest request = new PatternQueryRequest(
            getStringArg(args, "task"),
            getStringArg(args, "language"),
            getStringArg(args, "projectPath"),
            getStringArg(args, "conversationId"),
            args.has("topK") ? args.get("topK").asInt(10) : null);

        ResponseEntity<PatternQueryResponse> response = patternController.queryPatterns(request);
        String json = objectMapper.writeValueAsString(response.getBody());
        return textContent(json);
    }

    private JsonNode handleCapturePattern(JsonNode args) {
        PatternCaptureRequest request = new PatternCaptureRequest();
        request.setDescription(getStringArg(args, "description"));
        request.setSource(getStringArg(args, "source"));
        request.setProjectPath(getStringArg(args, "projectPath"));

        if (args.has("codeExample"))    request.setCodeExample(args.get("codeExample").asText());
        if (args.has("rationale"))      request.setRationale(args.get("rationale").asText());
        if (args.has("conversationId")) request.setConversationId(args.get("conversationId").asText());

        ResponseEntity<?> response = patternCaptureController.capturePattern(request);
        if (response.getBody() instanceof PatternCaptureResponse captured) {
            return textContent("Pattern captured successfully. ID: " + captured.patternId() + " — " + captured.message());
        }
        return textContent("Pattern captured successfully.");
    }

    private JsonNode handleGetStandards(JsonNode args) {
        ResponseEntity<String> response = standardsController.generateStandards(
            getStringArg(args, "projectPath"),
            getStringArg(args, "language"));
        String body = Objects.nonNull(response.getBody()) ? response.getBody() : "No standards available.";
        return textContent(body);
    }

    private JsonNode handleRecordUsage(JsonNode args) throws Exception {
        PatternUsageRequest request = new PatternUsageRequest();
        String patternIdStr = getStringArg(args, "patternId");
        if (Objects.nonNull(patternIdStr)) {
            request.setPatternId(UUID.fromString(patternIdStr));
        }
        request.setProjectPath(getStringArg(args, "projectPath"));
        request.setSuccess(args.has("success") && args.get("success").asBoolean());
        if (args.has("taskType")) {
            request.setTaskType(args.get("taskType").asText());
        }

        ResponseEntity<?> response = patternController.recordPatternUsage(request);
        String status = (response.getBody() instanceof PatternUsageResponse usage)
            ? usage.status()
            : "success";
        return textContent("Usage recorded — status: " + status);
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private JsonNode textContent(String text) {
        ArrayNode content = objectMapper.createArrayNode();
        ObjectNode block = objectMapper.createObjectNode();
        block.put("type", "text");
        block.put("text", text);
        content.add(block);
        return content;
    }

    private JsonNode errorContent(String message) {
        ArrayNode content = objectMapper.createArrayNode();
        ObjectNode block = objectMapper.createObjectNode();
        block.put("type", "text");
        block.put("text", "Error: " + message);
        content.add(block);

        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.set("content", content);
        wrapper.put("isError", true);
        return wrapper;
    }

    private String getStringArg(JsonNode args, String key) {
        return args.has(key) ? args.get(key).asText() : null;
    }

    private JsonNode buildToolDef(
            String name,
            String description,
            Map<String, ToolParameter> properties,
            String[] required) {

        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", name);
        tool.put("description", description);

        ObjectNode inputSchema = objectMapper.createObjectNode();
        inputSchema.put("type", "object");

        ObjectNode propsNode = objectMapper.createObjectNode();
        for (Map.Entry<String, ToolParameter> entry : properties.entrySet()) {
            ObjectNode prop = objectMapper.createObjectNode();
            prop.put("type", entry.getValue().type());
            prop.put("description", entry.getValue().description());
            propsNode.set(entry.getKey(), prop);
        }
        inputSchema.set("properties", propsNode);

        ArrayNode requiredNode = objectMapper.createArrayNode();
        for (String req : required) {
            requiredNode.add(req);
        }
        inputSchema.set("required", requiredNode);
        tool.set("inputSchema", inputSchema);

        return tool;
    }
}

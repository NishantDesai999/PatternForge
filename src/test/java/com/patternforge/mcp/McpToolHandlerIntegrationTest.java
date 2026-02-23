package com.patternforge.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patternforge.AbstractIntegrationTest;
import com.patternforge.jooq.tables.records.PatternsRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.patternforge.jooq.Tables.PATTERNS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for McpToolHandler — MCP tool dispatch and tool definitions.
 */
class McpToolHandlerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private McpToolHandler mcpToolHandler;

    @Autowired
    private ObjectMapper objectMapper;

    // ==================== getToolDefinitions ====================

    @Test
    void shouldReturnFourToolDefinitions() {
        JsonNode tools = mcpToolHandler.getToolDefinitions();

        assertThat(tools.isArray()).isTrue();
        assertThat(tools.size()).isEqualTo(4);
    }

    @Test
    void shouldIncludeQueryPatternsToolDefinition() {
        JsonNode tools = mcpToolHandler.getToolDefinitions();

        boolean hasQueryPatterns = false;
        for (JsonNode tool : tools) {
            if ("query_patterns".equals(tool.get("name").asText())) {
                hasQueryPatterns = true;
                assertThat(tool.get("description").asText()).isNotBlank();
                assertThat(tool.get("inputSchema")).isNotNull();
                assertThat(tool.get("inputSchema").get("required")).isNotNull();
                break;
            }
        }
        assertThat(hasQueryPatterns).isTrue();
    }

    @Test
    void shouldIncludeCapturePatternToolDefinition() {
        JsonNode tools = mcpToolHandler.getToolDefinitions();

        boolean hasCapturePattern = false;
        for (JsonNode tool : tools) {
            if ("capture_pattern".equals(tool.get("name").asText())) {
                hasCapturePattern = true;
                break;
            }
        }
        assertThat(hasCapturePattern).isTrue();
    }

    @Test
    void shouldIncludeGetStandardsToolDefinition() {
        JsonNode tools = mcpToolHandler.getToolDefinitions();

        boolean hasGetStandards = false;
        for (JsonNode tool : tools) {
            if ("get_standards".equals(tool.get("name").asText())) {
                hasGetStandards = true;
                break;
            }
        }
        assertThat(hasGetStandards).isTrue();
    }

    @Test
    void shouldIncludeRecordUsageToolDefinition() {
        JsonNode tools = mcpToolHandler.getToolDefinitions();

        boolean hasRecordUsage = false;
        for (JsonNode tool : tools) {
            if ("record_usage".equals(tool.get("name").asText())) {
                hasRecordUsage = true;
                break;
            }
        }
        assertThat(hasRecordUsage).isTrue();
    }

    @Test
    void shouldIncludeInputSchemaForEachTool() {
        JsonNode tools = mcpToolHandler.getToolDefinitions();

        for (JsonNode tool : tools) {
            String toolName = tool.get("name").asText();
            assertThat(tool.has("inputSchema"))
                    .as("Tool %s should have inputSchema", toolName)
                    .isTrue();
            assertThat(tool.get("inputSchema").has("properties"))
                    .as("Tool %s inputSchema should have properties", toolName)
                    .isTrue();
        }
    }

    // ==================== callTool — unknown tool ====================

    @Test
    void shouldReturnErrorForUnknownTool() {
        ObjectNode args = objectMapper.createObjectNode();
        JsonNode result = mcpToolHandler.callTool("unknown_tool_xyz", args);

        assertThat(result).isNotNull();
        // Error result has isError=true wrapper
        assertThat(result.has("isError")).isTrue();
        assertThat(result.get("isError").asBoolean()).isTrue();
    }

    // ==================== callTool — query_patterns ====================

    @Test
    void shouldCallQueryPatternsAndReturnTextContent() {
        // Arrange
        ObjectNode args = objectMapper.createObjectNode();
        args.put("task", "fix failing test in UserService");
        args.put("language", "java");

        // Act
        JsonNode result = mcpToolHandler.callTool("query_patterns", args);

        // Assert — returns array with text content blocks
        assertThat(result.isArray()).isTrue();
        assertThat(result.size()).isGreaterThan(0);
        assertThat(result.get(0).get("type").asText()).isEqualTo("text");
        assertThat(result.get(0).get("text").asText()).isNotBlank();
    }

    // ==================== callTool — capture_pattern ====================

    @Test
    void shouldCallCapturePatternSuccessfully() {
        // Arrange
        ObjectNode args = objectMapper.createObjectNode();
        args.put("description", "Always use Objects.requireNonNull for validation");
        args.put("source", "user_explicit");
        args.put("projectPath", "/test/mcp/project");

        // Act
        JsonNode result = mcpToolHandler.callTool("capture_pattern", args);

        // Assert
        assertThat(result.isArray()).isTrue();
        String text = result.get(0).get("text").asText();
        assertThat(text).contains("Pattern captured successfully");
    }

    // ==================== callTool — get_standards ====================

    @Test
    void shouldCallGetStandardsAndReturnTextContent() {
        // Arrange
        ObjectNode args = objectMapper.createObjectNode();
        args.put("projectPath", "/test/mcp/project");
        args.put("language", "java");

        // Act
        JsonNode result = mcpToolHandler.callTool("get_standards", args);

        // Assert
        assertThat(result.isArray()).isTrue();
        assertThat(result.get(0).get("type").asText()).isEqualTo("text");
    }

    // ==================== callTool — record_usage ====================

    @Test
    void shouldCallRecordUsageSuccessfully() {
        // Arrange — insert a pattern first
        UUID patternId = insertPattern("mcp-test-pattern", "MCP Test Pattern");

        ObjectNode args = objectMapper.createObjectNode();
        args.put("patternId", patternId.toString());
        args.put("projectPath", "/test/mcp/project");
        args.put("taskType", "fix_bug");
        args.put("success", true);

        // Act
        JsonNode result = mcpToolHandler.callTool("record_usage", args);

        // Assert
        assertThat(result.isArray()).isTrue();
        String text = result.get(0).get("text").asText();
        assertThat(text).contains("Usage recorded");
    }

    // ==================== Helpers ====================

    private UUID insertPattern(String name, String title) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(PATTERNS)
                .set(PATTERNS.PATTERN_ID, id)
                .set(PATTERNS.PATTERN_NAME, name)
                .set(PATTERNS.TITLE, title)
                .set(PATTERNS.DESCRIPTION, "Test description")
                .set(PATTERNS.CATEGORY, "test")
                .set(PATTERNS.WHEN_TO_USE, "For testing purposes")
                .set(PATTERNS.IS_PROJECT_STANDARD, false)
                .set(PATTERNS.IS_GLOBAL_STANDARD, false)
                .set(PATTERNS.LANGUAGES, new String[]{"java"})
                .set(PATTERNS.FRAMEWORKS, new String[0])
                .set(PATTERNS.APPLIES_TO, new String[0])
                .set(PATTERNS.CREATED_AT, LocalDateTime.now())
                .execute();
        return id;
    }
}

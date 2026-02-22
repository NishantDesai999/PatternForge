package com.patternforge.mcp;

/**
 * Represents a single parameter in an MCP tool's JSON Schema input definition.
 *
 * @param type        JSON Schema type (e.g., "string", "integer", "boolean")
 * @param description human-readable description shown to the agent
 */
public record ToolParameter(String type, String description) {}

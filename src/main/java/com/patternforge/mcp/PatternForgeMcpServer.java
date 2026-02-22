package com.patternforge.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patternforge.api.rest.PatternController;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;

/**
 * MCP server implementing the Model Context Protocol (JSON-RPC 2.0).
 *
 * <p>Starts on port 8765 when the application is ready. Exposes four tools:
 * <ul>
 *   <li>{@code query_patterns} – retrieve patterns + workflow for a task</li>
 *   <li>{@code capture_pattern} – capture a pattern from the current session</li>
 *   <li>{@code get_standards} – return all standards as a formatted document</li>
 *   <li>{@code record_usage} – record whether a pattern was applied successfully</li>
 * </ul>
 *
 * <p>The legacy {@code /mcp/query} HTTP endpoint is kept for backwards compatibility
 * with OpenCode and other tools that used it before the JSON-RPC upgrade.
 *
 * <h2>Configure in Claude Code</h2>
 * Add the following to {@code ~/.claude.json} (or the project's {@code .mcp.json}):
 * <pre>
 * {
 *   "mcpServers": {
 *     "patternforge": {
 *       "type": "http",
 *       "url": "http://localhost:8765/mcp"
 *     }
 *   }
 * }
 * </pre>
 *
 * <h2>Configure in other MCP-compatible tools</h2>
 * Point the tool's MCP server URL to {@code http://localhost:8765/mcp}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PatternForgeMcpServer {

    private static final String JSONRPC_VERSION = "2.0";
    private static final String SERVER_NAME = "patternforge";
    private static final String SERVER_VERSION = "1.1.0";
    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final PatternController patternController;
    private final McpToolHandler mcpToolHandler;
    private final ObjectMapper objectMapper;

    @EventListener(ApplicationReadyEvent.class)
    public void startMcpServer() {
        new Thread(() -> {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(8765), 0);

                // JSON-RPC 2.0 endpoint — used by Claude Code, Cursor, and other MCP clients
                server.createContext("/mcp", this::handleMcpRequest);

                // Legacy HTTP endpoint — kept for backwards compat with OpenCode
                server.createContext("/mcp/query", this::handleLegacyQueryRequest);

                server.setExecutor(null);
                server.start();

                log.info("PatternForge MCP server started on port 8765");
                log.info("  JSON-RPC endpoint : http://localhost:8765/mcp");
                log.info("  Legacy endpoint   : http://localhost:8765/mcp/query");
            } catch (IOException ioException) {
                log.error("Failed to start MCP server", ioException);
            }
        }).start();
    }

    // ─────────────────────────────────────────────────────────────
    // JSON-RPC 2.0 handler
    // ─────────────────────────────────────────────────────────────

    /**
     * Handles all JSON-RPC 2.0 requests on POST /mcp.
     * Routes based on the {@code method} field in the request body.
     */
    private void handleMcpRequest(HttpExchange exchange) {
        try {
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJsonRpcError(exchange, null, -32600, "Only POST is supported");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes());
            if (body.isBlank()) {
                sendJsonRpcError(exchange, null, -32700, "Empty request body");
                return;
            }

            JsonNode request = objectMapper.readTree(body);
            JsonNode idNode = request.get("id");
            String method = request.has("method") ? request.get("method").asText() : null;

            if (Objects.isNull(method)) {
                sendJsonRpcError(exchange, idNode, -32600, "Missing method field");
                return;
            }

            // Notifications (no id, no response needed) — just acknowledge silently
            if (Objects.isNull(idNode) || idNode.isNull()) {
                log.debug("Received MCP notification: {}", method);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            ObjectNode response = switch (method) {
                case "initialize" -> handleInitialize(idNode);
                case "tools/list" -> handleToolsList(idNode);
                case "tools/call" -> handleToolsCall(idNode, request.get("params"));
                default -> buildError(idNode, -32601, "Method not found: " + method);
            };

            sendJsonResponse(exchange, response, 200);

        } catch (Exception exception) {
            log.error("Error handling MCP request", exception);
            try {
                sendJsonRpcError(exchange, null, -32603, "Internal error: " + exception.getMessage());
            } catch (IOException responseException) {
                log.error("Failed to send error response", responseException);
            }
        } finally {
            exchange.close();
        }
    }

    private ObjectNode handleInitialize(JsonNode idNode) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);

        ObjectNode capabilities = objectMapper.createObjectNode();
        capabilities.set("tools", objectMapper.createObjectNode());
        result.set("capabilities", capabilities);

        ObjectNode serverInfo = objectMapper.createObjectNode();
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        result.set("serverInfo", serverInfo);

        log.info("MCP client connected (initialize handshake)");
        return buildResult(idNode, result);
    }

    private ObjectNode handleToolsList(JsonNode idNode) {
        ObjectNode result = objectMapper.createObjectNode();
        result.set("tools", mcpToolHandler.getToolDefinitions());
        return buildResult(idNode, result);
    }

    private ObjectNode handleToolsCall(JsonNode idNode, JsonNode params) {
        if (Objects.isNull(params)) {
            return buildError(idNode, -32602, "Missing params for tools/call");
        }

        String toolName = params.has("name") ? params.get("name").asText() : null;
        if (Objects.isNull(toolName) || toolName.isBlank()) {
            return buildError(idNode, -32602, "Missing tool name");
        }

        JsonNode arguments = params.has("arguments") ? params.get("arguments") : objectMapper.createObjectNode();

        log.info("MCP tool call: {}", toolName);
        JsonNode toolResult = mcpToolHandler.callTool(toolName, arguments);

        ObjectNode result = objectMapper.createObjectNode();
        if (toolResult.has("isError") && toolResult.get("isError").asBoolean()) {
            result.set("content", toolResult.get("content"));
            result.put("isError", true);
        } else {
            result.set("content", toolResult);
        }

        return buildResult(idNode, result);
    }

    // ─────────────────────────────────────────────────────────────
    // Legacy HTTP handler (backwards compat)
    // ─────────────────────────────────────────────────────────────

    private void handleLegacyQueryRequest(HttpExchange exchange) {
        try {
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes());
                Map<String, Object> request = objectMapper.readValue(body, Map.class);

                ResponseEntity<Map<String, Object>> response = patternController.queryPatterns(request);

                byte[] responseBytes = objectMapper.writeValueAsBytes(response.getBody());
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(responseBytes);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        } catch (IOException ioException) {
            log.error("Error handling legacy MCP request", ioException);
            try {
                exchange.sendResponseHeaders(500, -1);
            } catch (IOException responseException) {
                log.error("Error sending error response", responseException);
            }
        } finally {
            exchange.close();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // JSON-RPC helpers
    // ─────────────────────────────────────────────────────────────

    private ObjectNode buildResult(JsonNode idNode, JsonNode result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", JSONRPC_VERSION);
        response.set("id", idNode);
        response.set("result", result);
        return response;
    }

    private ObjectNode buildError(JsonNode idNode, int code, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", JSONRPC_VERSION);
        response.set("id", idNode);

        ObjectNode error = objectMapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        response.set("error", error);

        return response;
    }

    private void sendJsonRpcError(HttpExchange exchange, JsonNode idNode, int code, String message)
            throws IOException {
        ObjectNode errorResponse = buildError(
            Objects.nonNull(idNode) ? idNode : objectMapper.nullNode(),
            code, message
        );
        sendJsonResponse(exchange, errorResponse, 200);
    }

    private void sendJsonResponse(HttpExchange exchange, ObjectNode body, int statusCode)
            throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }
}

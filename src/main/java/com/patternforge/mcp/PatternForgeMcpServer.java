package com.patternforge.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Minimal MCP server that delegates to PatternController.
 * Starts on port 8765 when application is ready.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PatternForgeMcpServer {
    
    private final PatternController patternController;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @EventListener(ApplicationReadyEvent.class)
    public void startMcpServer() {
        new Thread(() -> {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(8765), 0);
                
                server.createContext("/mcp/query", this::handleQueryRequest);
                
                server.setExecutor(null);
                server.start();
                
                log.info("PatternForge MCP Server started on port 8765");
            } catch (IOException ioException) {
                log.error("Failed to start MCP server", ioException);
            }
        }).start();
    }
    
    private void handleQueryRequest(HttpExchange exchange) {
        try {
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
            log.error("Error handling MCP request", ioException);
            try {
                exchange.sendResponseHeaders(500, -1);
            } catch (IOException responseException) {
                log.error("Error sending error response", responseException);
            }
        } finally {
            exchange.close();
        }
    }
}

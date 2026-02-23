package com.patternforge.llm;

/**
 * Exception thrown when Ollama API calls fail.
 */
public class OllamaApiException extends RuntimeException {

    public OllamaApiException(String message) {
        super(message);
    }

    public OllamaApiException(String message, Throwable cause) {
        super(message, cause);
    }
}

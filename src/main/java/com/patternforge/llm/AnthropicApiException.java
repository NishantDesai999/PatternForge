package com.patternforge.llm;

/**
 * Thrown when the Anthropic API returns an error or an unexpected response.
 * Caught separately in PatternController to return a clear HTTP 500 message.
 */
public class AnthropicApiException extends RuntimeException {

    public AnthropicApiException(String message) {
        super(message);
    }

    public AnthropicApiException(String message, Throwable cause) {
        super(message, cause);
    }
}

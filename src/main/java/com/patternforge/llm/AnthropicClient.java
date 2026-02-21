package com.patternforge.llm;

import com.patternforge.config.AnthropicProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Thin client for the Anthropic Messages API.
 * Follows the EmbeddingService constructor-injection pattern:
 * manual constructor with WebClient.Builder + properties parameters.
 */
@Component
@Slf4j
public class AnthropicClient {

    private final WebClient webClient;
    private final AnthropicProperties properties;

    public AnthropicClient(AnthropicProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.webClient = webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("x-api-key", properties.getApiKey())
                .defaultHeader("anthropic-version", properties.getApiVersion())
                .defaultHeader("content-type", "application/json")
                .build();
    }

    /**
     * Sends a single user message to the Anthropic Messages API and returns the text response.
     *
     * @param model      model identifier (e.g., "claude-sonnet-4-6")
     * @param maxTokens  maximum tokens in the completion
     * @param userPrompt user message content
     * @return response text from the model
     * @throws AnthropicApiException if the API call fails or returns an empty response
     */
    @SuppressWarnings("unchecked")
    public String complete(String model, int maxTokens, String userPrompt) {
        log.debug("Calling Anthropic API: model={}, maxTokens={}", model, maxTokens);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "messages", List.of(
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        try {
            Map<String, Object> response = webClient.post()
                    .uri("/v1/messages")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            if (Objects.isNull(response)) {
                throw new AnthropicApiException("Anthropic API returned null response");
            }

            List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
            if (Objects.isNull(content) || content.isEmpty()) {
                throw new AnthropicApiException("Anthropic API response contained no content blocks");
            }

            String text = (String) content.get(0).get("text");
            if (Objects.isNull(text) || text.isBlank()) {
                throw new AnthropicApiException("Anthropic API response content text was empty");
            }

            log.debug("Anthropic API call succeeded, response length={}", text.length());
            return text;

        } catch (AnthropicApiException anthropicApiException) {
            throw anthropicApiException;
        } catch (Exception exception) {
            throw new AnthropicApiException("Anthropic API call failed: " + exception.getMessage(), exception);
        }
    }
}

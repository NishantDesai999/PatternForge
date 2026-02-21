package com.patternforge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the Anthropic API client.
 */
@Component
@ConfigurationProperties(prefix = "patternforge.anthropic")
@Data
public class AnthropicProperties {

    private String apiKey;
    private String baseUrl = "https://api.anthropic.com";
    private String apiVersion = "2023-06-01";
}

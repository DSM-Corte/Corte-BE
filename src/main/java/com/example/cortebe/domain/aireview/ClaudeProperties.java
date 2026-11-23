package com.example.cortebe.domain.aireview;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("claude")
public record ClaudeProperties(
    String apiKey,
    String model
) {
}

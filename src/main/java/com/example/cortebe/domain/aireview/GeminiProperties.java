package com.example.cortebe.domain.aireview;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gemini")
public record GeminiProperties(
    String apiKey,
    String model
) {
}

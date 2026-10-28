package com.example.cortebe.domain.judge;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("judge0")
public record Judge0Properties(
    String url,
    Duration pollInterval,
    Duration idleTimeout
) {
}

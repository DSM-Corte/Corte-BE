package com.example.cortebe.domain.judge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("judge0")
public record Judge0Properties(
    @DefaultValue("http://localhost:2358") String url,
    @DefaultValue("1s") Duration pollInterval,
    @DefaultValue("2m") Duration idleTimeout
) {
    public Judge0Properties {
        if (pollInterval.isZero() || pollInterval.isNegative() || idleTimeout.isZero() || idleTimeout.isNegative())
            throw new IllegalArgumentException("Judge0 대기 시간 설정은 양수여야 합니다.");
    }
}

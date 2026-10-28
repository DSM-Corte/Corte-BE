package com.example.cortebe.domain.judge.runner;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("judge")
public record JudgeProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("docker") String docker,
    @DefaultValue("2") int concurrency,
    @DefaultValue("16") int queueCapacity,
    @DefaultValue("30") int requestsPerMinute,
    @DefaultValue("120") int sessionSeconds,
    @DefaultValue("60") int compileSeconds,
    @DefaultValue("1048576") int outputBytes
) {
    public JudgeProperties {
        if (concurrency < 1 || queueCapacity < 1 || requestsPerMinute < 1 || sessionSeconds < 1
            || compileSeconds < 1 || outputBytes < 1) throw new IllegalArgumentException("채점 설정값은 양수여야 합니다.");
    }
}

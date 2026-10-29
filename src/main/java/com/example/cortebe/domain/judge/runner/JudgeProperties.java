package com.example.cortebe.domain.judge.runner;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties("judge")
public record JudgeProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("2") int concurrency,
    @DefaultValue("16") int queueCapacity,
    @DefaultValue("30") int requestsPerMinute,
    @DefaultValue("1") int maxConcurrentPerIdentity
) {
    @ConstructorBinding
    public JudgeProperties {
        if (concurrency < 1 || queueCapacity < 1 || requestsPerMinute < 1 || maxConcurrentPerIdentity < 1)
            throw new IllegalArgumentException("채점 설정값은 양수여야 합니다.");
    }

}

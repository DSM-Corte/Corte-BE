package com.example.cortebe.domain.problem.dto;

import lombok.Builder;

import java.util.Set;

@Builder
public record ProblemResponse(
    Long id,
    String title,
    String description,
    String category,
    Integer memoryLimitMb,
    Integer timeLimitMs,
    Set<String> tags
) {
}

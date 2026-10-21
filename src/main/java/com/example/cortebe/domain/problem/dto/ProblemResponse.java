package com.example.cortebe.domain.problem.dto;

import lombok.Builder;

@Builder
public record ProblemResponse(
    Long id,
    String title,
    String description,
    String category,
    Integer memoryLimitMb,
    Integer timeLimitMs
) {
}

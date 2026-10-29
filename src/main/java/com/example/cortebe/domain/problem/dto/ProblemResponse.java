package com.example.cortebe.domain.problem.dto;

import com.example.cortebe.domain.judge.runner.Language;
import lombok.Builder;

import java.util.Set;
import java.util.Map;

@Builder
public record ProblemResponse(
    Long id,
    String title,
    String description,
    String category,
    Integer memoryLimitMb,
    Integer timeLimitMs,
    Set<String> tags,
    Map<Language, Integer> languageMemoryLimitsMb
) {
}

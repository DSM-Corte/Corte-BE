package com.example.cortebe.domain.github.event;

import lombok.Builder;

@Builder
public record ProblemSolvedEvent(
    long installationId,
    String owner,
    String repo,
    long problemId,
    String title,
    String description,
    String language,
    String fileExtension,
    String sourceCode
) {
}

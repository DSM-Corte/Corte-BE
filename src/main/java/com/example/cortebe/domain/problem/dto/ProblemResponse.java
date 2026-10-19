package com.example.cortebe.domain.problem.dto;

public record ProblemResponse(
    Long id,
    String title,
    String description,
    String inputFormat,
    String outputFormat,
    String exampleInput,
    String exampleOutput
) {
}

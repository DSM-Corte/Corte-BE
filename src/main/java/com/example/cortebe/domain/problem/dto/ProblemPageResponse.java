package com.example.cortebe.domain.problem.dto;

import java.util.List;

public record ProblemPageResponse(
    List<Summary> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public record Summary(Long id, String title) {
    }
}

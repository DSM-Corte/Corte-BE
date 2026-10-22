package com.example.cortebe.domain.problem.dto;

import lombok.Builder;

import java.util.List;
import java.util.Set;

@Builder
public record ProblemPageResponse(
    List<Summary> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    @Builder
    public record Summary(Long id, String title, String category, Set<String> tags) {
    }
}

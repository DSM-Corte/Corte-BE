package com.example.cortebe.domain.review.dto;

import com.example.cortebe.domain.judge.runner.Language;

import java.time.LocalDateTime;
import java.util.List;

public record ReviewRequestPageResponse(
    List<Summary> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public record Summary(Long id, Long problemId, String problemTitle, Long requesterId, String requester,
                          Language language, boolean closed, LocalDateTime createdAt) {
    }
}

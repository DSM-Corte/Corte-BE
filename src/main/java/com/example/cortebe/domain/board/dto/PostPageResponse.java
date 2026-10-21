package com.example.cortebe.domain.board.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PostPageResponse(
    List<Summary> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public record Summary(Long id, String title, String author, LocalDateTime createdAt) {
    }
}

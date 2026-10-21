package com.example.cortebe.domain.board.dto;

import java.time.LocalDateTime;

public record PostResponse(
    Long id,
    String title,
    String content,
    String author,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}

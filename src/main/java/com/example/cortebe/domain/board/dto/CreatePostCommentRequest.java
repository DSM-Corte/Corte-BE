package com.example.cortebe.domain.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePostCommentRequest(
    @NotBlank @Size(max = 500)
    String content
) {
}

package com.example.cortebe.domain.board.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdatePostCommentRequest(
    @Pattern(regexp = "(?s).*\\S.*") @Size(max = 500)
    String content
) {
}

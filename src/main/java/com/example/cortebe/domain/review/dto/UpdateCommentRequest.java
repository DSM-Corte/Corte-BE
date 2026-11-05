package com.example.cortebe.domain.review.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCommentRequest(
    @Pattern(regexp = "(?s).*\\S.*") @Size(max = 20000)
    String content
) {
}

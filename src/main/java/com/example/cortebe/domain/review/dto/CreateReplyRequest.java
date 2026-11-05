package com.example.cortebe.domain.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateReplyRequest(
    @NotBlank @Size(max = 20000)
    String content
) {
}

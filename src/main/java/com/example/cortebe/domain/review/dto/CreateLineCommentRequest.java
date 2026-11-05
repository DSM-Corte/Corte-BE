package com.example.cortebe.domain.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateLineCommentRequest(
    @NotNull @Positive
    Integer lineNumber,

    @NotBlank @Size(max = 20000)
    String content,

    @Size(max = 20000)
    String suggestedCode
) {
}

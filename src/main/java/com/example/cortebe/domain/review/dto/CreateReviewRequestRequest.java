package com.example.cortebe.domain.review.dto;

import jakarta.validation.constraints.NotNull;

public record CreateReviewRequestRequest(
    @NotNull
    Long submissionId
) {
}

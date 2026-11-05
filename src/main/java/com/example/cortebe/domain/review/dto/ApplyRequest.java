package com.example.cortebe.domain.review.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ApplyRequest(
    @NotNull
    List<Long> lineCommentIds
) {
}

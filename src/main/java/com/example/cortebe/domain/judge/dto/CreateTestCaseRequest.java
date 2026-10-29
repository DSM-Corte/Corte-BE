package com.example.cortebe.domain.judge.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateTestCaseRequest(
    @NotNull @Size(max = 1048576) String input,
    @NotNull @Size(max = 1048576) String expectedOutput,
    @NotNull Boolean publicExample,
    @NotNull @PositiveOrZero Integer position
) {
}

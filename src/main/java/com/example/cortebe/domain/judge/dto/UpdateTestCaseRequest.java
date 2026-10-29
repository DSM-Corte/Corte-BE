package com.example.cortebe.domain.judge.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateTestCaseRequest(
    @Size(max = 1048576) String input,
    @Size(max = 1048576) String expectedOutput,
    Boolean publicExample,
    @PositiveOrZero Integer position
) {
}

package com.example.cortebe.domain.problem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateProblemRequest(
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 20000) String description,
    @NotBlank @Size(max = 50) String category,
    @NotNull @Positive Integer memoryLimitMb,
    @NotNull @Positive Integer timeLimitMs
) {
}

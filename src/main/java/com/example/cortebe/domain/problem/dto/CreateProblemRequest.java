package com.example.cortebe.domain.problem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProblemRequest(
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 20000) String description,
    @NotBlank @Size(max = 10000) String inputFormat,
    @NotBlank @Size(max = 10000) String outputFormat,
    @NotNull @Size(max = 10000) String exampleInput,
    @NotNull @Size(max = 10000) String exampleOutput
) {
}

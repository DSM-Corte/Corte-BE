package com.example.cortebe.domain.problem.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProblemRequest(
    @Pattern(regexp = "(?s).*\\S.*") @Size(max = 200) String title,
    @Pattern(regexp = "(?s).*\\S.*") @Size(max = 20000) String description,
    @Pattern(regexp = "(?s).*\\S.*") @Size(max = 10000) String inputFormat,
    @Pattern(regexp = "(?s).*\\S.*") @Size(max = 10000) String outputFormat,
    @Size(max = 10000) String exampleInput,
    @Size(max = 10000) String exampleOutput
) {
}

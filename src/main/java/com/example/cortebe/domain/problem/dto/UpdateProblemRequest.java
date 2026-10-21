package com.example.cortebe.domain.problem.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateProblemRequest(
    @Pattern(regexp = "(?s).*\\S.*") @Size(max = 200) String title,
    @Pattern(regexp = "(?s).*\\S.*") @Size(max = 20000) String description,
    @Pattern(regexp = "(?s).*\\S.*") @Size(max = 50) String category,
    @Positive Integer memoryLimitMb,
    @Positive Integer timeLimitMs
) {
}

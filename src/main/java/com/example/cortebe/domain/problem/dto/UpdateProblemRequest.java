package com.example.cortebe.domain.problem.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateProblemRequest(
    @Pattern(regexp = "(?s).*\\S.*") @Size(max = 200) String title,
    @Pattern(regexp = "(?s).*\\S.*") @Size(max = 20000) String description,
    @Pattern(regexp = "(?s).*\\S.*") @Size(max = 50) String category,
    @Positive Integer memoryLimitMb,
    @Positive Integer timeLimitMs,
    @Size(max = 10) Set<@NotBlank @Size(max = 30) String> tags
) {
}

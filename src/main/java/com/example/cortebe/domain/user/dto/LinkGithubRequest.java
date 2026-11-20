package com.example.cortebe.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record LinkGithubRequest(
    @NotNull @Positive Long installationId,
    @NotBlank String owner,
    @NotBlank String repo
) {
}

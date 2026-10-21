package com.example.cortebe.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
    @NotBlank @Email @Size(max = 100)
    String email,

    @NotBlank @Size(min = 8, max = 20)
    String password,

    @NotBlank @Size(max = 20)
    String nickname
) {
}

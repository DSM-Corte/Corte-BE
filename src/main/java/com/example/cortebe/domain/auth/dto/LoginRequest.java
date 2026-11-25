package com.example.cortebe.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank
    String email,

    @NotBlank
    String password,

    // 로그인 상태 유지. 생략하면 false로 본다
    Boolean rememberMe
) {
}

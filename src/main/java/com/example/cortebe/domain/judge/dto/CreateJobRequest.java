package com.example.cortebe.domain.judge.dto;

import com.example.cortebe.domain.judge.runner.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateJobRequest(@NotNull @Positive Long problemId, @NotNull Language language,
                               @NotBlank @Size(max = 102400) String code,
                               @Size(max = 1048576) String stdin) {
    public CreateJobRequest(Long problemId, Language language, String code) {
        this(problemId, language, code, null);
    }
}

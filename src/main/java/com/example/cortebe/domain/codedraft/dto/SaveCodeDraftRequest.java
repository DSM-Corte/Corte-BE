package com.example.cortebe.domain.codedraft.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveCodeDraftRequest(@NotNull @Size(max = 102400) String code) {
}

package com.example.cortebe.domain.workbook.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddWorkbookProblemRequest(@NotNull @Positive Long problemId) {
}

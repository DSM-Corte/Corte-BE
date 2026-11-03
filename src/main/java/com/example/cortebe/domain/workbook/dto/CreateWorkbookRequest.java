package com.example.cortebe.domain.workbook.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateWorkbookRequest(@NotBlank String name) {
}

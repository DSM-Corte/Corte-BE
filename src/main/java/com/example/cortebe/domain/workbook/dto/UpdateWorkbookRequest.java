package com.example.cortebe.domain.workbook.dto;

import jakarta.validation.constraints.Pattern;

public record UpdateWorkbookRequest(@Pattern(regexp = "(?s).*\\S.*") String name) {
}

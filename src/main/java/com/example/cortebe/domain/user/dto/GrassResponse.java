package com.example.cortebe.domain.user.dto;

import java.time.LocalDate;

public record GrassResponse(
    LocalDate date,
    int count
) {
}

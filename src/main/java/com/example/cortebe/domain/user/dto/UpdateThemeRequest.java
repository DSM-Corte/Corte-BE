package com.example.cortebe.domain.user.dto;

import com.example.cortebe.domain.user.entity.ThemeColor;
import com.example.cortebe.domain.user.entity.ThemeMode;

public record UpdateThemeRequest(
    ThemeMode mode,
    ThemeColor color
) {
}

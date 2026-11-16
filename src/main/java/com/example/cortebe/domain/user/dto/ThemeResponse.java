package com.example.cortebe.domain.user.dto;

import com.example.cortebe.domain.user.entity.ThemeColor;
import com.example.cortebe.domain.user.entity.ThemeMode;
import com.example.cortebe.domain.user.entity.User;
import lombok.Builder;

@Builder
public record ThemeResponse(
    ThemeMode mode,
    ThemeColor color
) {
    public static ThemeResponse from(User user) {
        // 한 번도 고르지 않은 회원은 기본 테마(LIGHT, BLUE)로 응답한다
        ThemeMode mode = user.getThemeMode();
        if (mode == null) {
            mode = ThemeMode.LIGHT;
        }

        ThemeColor color = user.getThemeColor();
        if (color == null) {
            color = ThemeColor.BLUE;
        }

        return ThemeResponse.builder()
            .mode(mode)
            .color(color)
            .build();
    }
}

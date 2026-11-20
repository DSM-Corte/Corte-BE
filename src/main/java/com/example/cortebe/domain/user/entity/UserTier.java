package com.example.cortebe.domain.user.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserTier {
    BRONZE_3(0), BRONZE_2(30), BRONZE_1(60),
    SILVER_3(100), SILVER_2(160), SILVER_1(220),
    GOLD_3(300), GOLD_2(400), GOLD_1(500),
    PLATINUM_3(600), PLATINUM_2(730), PLATINUM_1(860),
    DIAMOND_3(1000), DIAMOND_2(1200), DIAMOND_1(1400);

    private final long minimumScore;

    public static UserTier fromScore(long score) {
        UserTier result = BRONZE_3;
        for (UserTier tier : values()) {
            if (score < tier.minimumScore) {
                break;
            }
            result = tier;
        }
        return result;
    }

    public UserTier next() {
        return this == DIAMOND_1 ? null : values()[ordinal() + 1];
    }
}

package com.example.cortebe.domain.leaderboard.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 사용자 티어와 그 티어가 되는 데 필요한 누적 XP(rank.md 3절). 낮은 티어부터 적는다
@Getter
@AllArgsConstructor
public enum UserTier {
    BRONZE_5(0), BRONZE_4(50), BRONZE_3(120), BRONZE_2(220), BRONZE_1(350),
    SILVER_5(550), SILVER_4(800), SILVER_3(1100), SILVER_2(1450), SILVER_1(1850),
    GOLD_5(2400), GOLD_4(3100), GOLD_3(4000), GOLD_2(5100), GOLD_1(6400),
    PLATINUM_5(8000), PLATINUM_4(10000), PLATINUM_3(12500), PLATINUM_2(15500), PLATINUM_1(19000),
    EMERALD_5(24000), EMERALD_4(30000), EMERALD_3(37000), EMERALD_2(45000), EMERALD_1(54000),
    DIAMOND_5(65000), DIAMOND_4(78000), DIAMOND_3(93000), DIAMOND_2(110000), DIAMOND_1(130000),
    MASTER(160000);

    private final long minXp;

    // 누적 XP 이상인 가장 높은 티어
    public static UserTier from(long xp) {
        UserTier result = BRONZE_5;
        for (UserTier tier : values()) {
            if (xp >= tier.minXp) {
                result = tier;
            }
        }
        return result;
    }
}

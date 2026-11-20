package com.example.cortebe.domain.user.entity;

import com.example.cortebe.domain.user.dto.UserTierResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class UserTierTests {

    @ParameterizedTest
    @CsvSource({
        "0, BRONZE_3", "29, BRONZE_3", "30, BRONZE_2", "31, BRONZE_2",
        "59, BRONZE_2", "60, BRONZE_1", "61, BRONZE_1",
        "99, BRONZE_1", "100, SILVER_3", "101, SILVER_3",
        "159, SILVER_3", "160, SILVER_2", "161, SILVER_2",
        "219, SILVER_2", "220, SILVER_1", "221, SILVER_1",
        "299, SILVER_1", "300, GOLD_3", "301, GOLD_3",
        "399, GOLD_3", "400, GOLD_2", "401, GOLD_2",
        "499, GOLD_2", "500, GOLD_1", "501, GOLD_1",
        "599, GOLD_1", "600, PLATINUM_3", "601, PLATINUM_3",
        "729, PLATINUM_3", "730, PLATINUM_2", "731, PLATINUM_2",
        "859, PLATINUM_2", "860, PLATINUM_1", "861, PLATINUM_1",
        "999, PLATINUM_1", "1000, DIAMOND_3", "1001, DIAMOND_3",
        "1199, DIAMOND_3", "1200, DIAMOND_2", "1201, DIAMOND_2",
        "1399, DIAMOND_2", "1400, DIAMOND_1", "1401, DIAMOND_1"
    })
    void 승급_기준_직전과_정확한_기준과_직후를_구분한다(long score, UserTier expected) {
        // when & then
        assertThat(UserTier.fromScore(score)).isEqualTo(expected);
    }

    @Test
    void 최고_단계_이후에도_점수_범위를_유지한다() {
        // when & then
        assertThat(UserTier.fromScore(Long.MAX_VALUE)).isEqualTo(UserTier.DIAMOND_1);
        assertThat(UserTierResponse.from(1400).nextTier()).isNull();
        assertThat(UserTierResponse.from(Long.MAX_VALUE).pointsToNextTier()).isZero();
    }
}

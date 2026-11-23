package com.example.cortebe.domain.leaderboard.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTierTests {

    @Test
    void 누적_XP_이상인_가장_높은_단계를_고른다() {
        // when & then
        assertThat(UserTier.from(0)).isEqualTo(UserTier.BRONZE_5);
        assertThat(UserTier.from(49)).isEqualTo(UserTier.BRONZE_5);
        assertThat(UserTier.from(50)).isEqualTo(UserTier.BRONZE_4);
        assertThat(UserTier.from(24000)).isEqualTo(UserTier.EMERALD_5);
        assertThat(UserTier.from(159999)).isEqualTo(UserTier.DIAMOND_1);
        assertThat(UserTier.from(160000)).isEqualTo(UserTier.MASTER);
    }

    @Test
    void 단계는_브론즈_5부터_마스터까지_31개다() {
        // when & then
        assertThat(UserTier.values()).hasSize(31);
        assertThat(UserTier.values()[0]).isEqualTo(UserTier.BRONZE_5);
        assertThat(UserTier.values()[30]).isEqualTo(UserTier.MASTER);
    }
}

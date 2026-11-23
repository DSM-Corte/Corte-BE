package com.example.cortebe.domain.user.dto;

import com.example.cortebe.domain.user.entity.UserTier;
import lombok.Builder;

@Builder
public record UserTierResponse(long score, UserTier tier, UserTier nextTier, long pointsToNextTier) {

    public static UserTierResponse from(long score) {
        UserTier tier = UserTier.fromScore(score);
        UserTier next = tier.next();
        return UserTierResponse.builder()
            .score(score)
            .tier(tier)
            .nextTier(next)
            .pointsToNextTier(next == null ? 0 : next.getMinimumScore() - score)
            .build();
    }
}

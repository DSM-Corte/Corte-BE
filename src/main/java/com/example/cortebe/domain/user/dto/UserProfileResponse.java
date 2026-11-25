package com.example.cortebe.domain.user.dto;

import com.example.cortebe.domain.user.entity.User;
import lombok.Builder;

// 다른 사용자에게 보여주는 프로필. 이메일은 넣지 않는다
@Builder
public record UserProfileResponse(
    Long userId,
    String nickname,
    UserStatsResponse stats
) {
    public static UserProfileResponse from(User user, UserStatsResponse stats) {
        return UserProfileResponse.builder()
            .userId(user.getId())
            .nickname(user.getNickname())
            .stats(stats)
            .build();
    }
}

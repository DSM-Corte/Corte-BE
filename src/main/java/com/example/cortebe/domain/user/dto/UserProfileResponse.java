package com.example.cortebe.domain.user.dto;

// 다른 사용자에게 보여주는 프로필. 이메일은 넣지 않는다
public record UserProfileResponse(
    Long userId,
    String nickname,
    UserStatsResponse stats
) {
}

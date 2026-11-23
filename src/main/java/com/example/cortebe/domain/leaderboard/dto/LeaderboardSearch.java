package com.example.cortebe.domain.leaderboard.dto;

import com.example.cortebe.domain.user.entity.UserTier;
import lombok.Builder;
import org.springframework.data.domain.Sort;

// 리더보드 조회 조건. null인 조건은 걸지 않는다. direction이 null이면 정렬 기준에 맞는 기본 방향을 쓴다
@Builder
public record LeaderboardSearch(
    int page,
    int size,
    String keyword,
    UserTier minTier,
    UserTier maxTier,
    Integer minSolved,
    Integer maxSolved,
    LeaderboardSort sort,
    Sort.Direction direction
) {
}

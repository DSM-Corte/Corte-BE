package com.example.cortebe.domain.leaderboard.dto;

import com.example.cortebe.domain.leaderboard.entity.UserTier;
import lombok.Builder;

import java.util.List;

public record LeaderboardPageResponse(
    List<Entry> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    @Builder
    public record Entry(
        int rank,
        Long userId,
        String nickname,
        UserTier tier,
        long xp,
        long solvedCount,
        long submissionCount,
        double accuracy,
        int streak,
        boolean me
    ) {
    }
}

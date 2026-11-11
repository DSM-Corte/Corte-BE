package com.example.cortebe.domain.user.dto;

import com.example.cortebe.domain.problem.entity.Difficulty;
import com.example.cortebe.domain.problem.repository.ProblemRepository.ProblemStats;
import lombok.Builder;

@Builder
public record ProblemStatsResponse(String category, Difficulty difficulty, long attemptedCount, long solvedCount) {

    public static ProblemStatsResponse from(ProblemStats stats) {
        return ProblemStatsResponse.builder()
            .category(stats.getCategory())
            .difficulty(stats.getDifficulty())
            .attemptedCount(stats.getAttemptedCount())
            .solvedCount(stats.getSolvedCount())
            .build();
    }
}

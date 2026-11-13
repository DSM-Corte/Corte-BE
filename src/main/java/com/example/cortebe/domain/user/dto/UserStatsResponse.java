package com.example.cortebe.domain.user.dto;

import com.example.cortebe.domain.judge.runner.Verdict;
import com.example.cortebe.domain.problem.entity.Difficulty;
import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record UserStatsResponse(
    long solvedCount,
    long submissionCount,
    long acceptedCount,
    double accuracy,
    Map<Verdict, Long> verdicts,
    List<Group> languages,
    List<DifficultyCount> difficulties,
    List<Group> tags,
    List<Group> categories,
    HardestProblem hardestProblem
) {
    // 언어·태그·분류별 통계 한 줄
    public record Group(String name, long solvedCount, long submissionCount, long acceptedCount, double accuracy) {
    }

    public record DifficultyCount(Difficulty difficulty, long solvedCount) {
    }

    public record HardestProblem(Long problemId, String title, Difficulty difficulty) {
    }
}

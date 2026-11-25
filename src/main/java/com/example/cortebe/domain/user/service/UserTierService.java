package com.example.cortebe.domain.user.service;

import com.example.cortebe.domain.judge.entity.JudgeJob;
import com.example.cortebe.domain.judge.runner.Verdict;
import com.example.cortebe.domain.problem.entity.Difficulty;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.domain.user.dto.UserTierResponse;
import com.example.cortebe.domain.user.entity.ProblemReward;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.ProblemRewardRepository;
import com.example.cortebe.domain.user.repository.ProblemRewardRepository.UnrecordedProblem;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.security.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserTierService {

    private final ProblemRewardRepository rewards;
    private final UserRepository users;
    private final ProblemRepository problems;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserTierResponse get(Long userId) {
        User user = users.findLockedById(userId).orElseThrow(UnauthorizedException::new);
        // 기존 정답만 작은 묶음으로 반영한다. 0점 기록도 남겨 난이도 변경만으로 지급되지 않게 한다.
        List<UnrecordedProblem> batch;
        do {
            batch = rewards.findUnrecordedSolvedProblems(userId, Verdict.ACCEPTED, PageRequest.of(0, 100));
            rewards.saveAllAndFlush(batch.stream().map(row -> ProblemReward.builder()
                .user(user)
                .problem(problems.getReferenceById(row.getProblemId()))
                .points(points(row.getDifficulty()))
                .build()).toList());
        } while (batch.size() == 100);
        return UserTierResponse.from(rewards.sumPoints(userId));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void award(JudgeJob job) {
        if (!job.isSubmission() || job.getAuthor() == null || job.getVerdict() != Verdict.ACCEPTED) {
            return;
        }
        User user = users.findLockedById(job.getAuthor().getId()).orElseThrow(UnauthorizedException::new);
        long points = points(job.getProblem().getDifficulty());
        rewards.findByUserIdAndProblemId(user.getId(), job.getProblem().getId())
            .ifPresentOrElse(reward -> reward.award(points), () -> rewards.saveAndFlush(ProblemReward.builder()
                .user(user).problem(job.getProblem()).points(points).build()));
    }

    // 리더보드도 아직 지급 기록이 없는 정답 문제를 같은 점수로 계산하려고 공개한다
    public static long points(Difficulty difficulty) {
        return switch (difficulty) {
            case BRONZE_5, BRONZE_4, BRONZE_3, BRONZE_2, BRONZE_1 -> 10;
            case SILVER_5, SILVER_4, SILVER_3, SILVER_2, SILVER_1 -> 20;
            case GOLD_5, GOLD_4, GOLD_3, GOLD_2, GOLD_1 -> 30;
            case PLATINUM_5, PLATINUM_4, PLATINUM_3, PLATINUM_2, PLATINUM_1 -> 40;
            case DIAMOND_5, DIAMOND_4, DIAMOND_3, DIAMOND_2, DIAMOND_1 -> 50;
            case RUBY_5, RUBY_4, RUBY_3, RUBY_2, RUBY_1 -> 60;
            case UNRATED -> 0;
        };
    }
}

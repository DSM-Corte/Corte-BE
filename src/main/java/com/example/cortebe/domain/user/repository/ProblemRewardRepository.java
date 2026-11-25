package com.example.cortebe.domain.user.repository;

import com.example.cortebe.domain.judge.runner.Verdict;
import com.example.cortebe.domain.problem.entity.Difficulty;
import com.example.cortebe.domain.user.entity.ProblemReward;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProblemRewardRepository extends JpaRepository<ProblemReward, Long> {

    Optional<ProblemReward> findByUserIdAndProblemId(Long userId, Long problemId);

    @Query("select coalesce(sum(r.points), 0) from ProblemReward r where r.user.id = :userId")
    long sumPoints(Long userId);

    @Query("""
        select distinct p.id as problemId, p.difficulty as difficulty
        from JudgeJob j join j.problem p
        where j.author.id = :userId and j.submission = true and j.verdict = :verdict
          and not exists (select r.id from ProblemReward r where r.user.id = :userId and r.problem.id = p.id)
        order by p.id
        """)
    List<UnrecordedProblem> findUnrecordedSolvedProblems(Long userId, Verdict verdict, Pageable pageable);

    // 리더보드용: 모든 회원의 지급 기록을 회원·문제·점수만 가져온다
    @Query("select r.user.id as userId, r.problem.id as problemId, r.points as points from ProblemReward r")
    List<RewardRow> findAllRewardRows();

    interface RewardRow {
        Long getUserId();
        Long getProblemId();
        Long getPoints();
    }

    interface UnrecordedProblem {
        Long getProblemId();
        Difficulty getDifficulty();
    }
}

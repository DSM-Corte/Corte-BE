package com.example.cortebe.domain.judge.repository;

import com.example.cortebe.domain.judge.entity.JudgeJob;
import com.example.cortebe.domain.judge.runner.Verdict;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JudgeJobRepository extends JpaRepository<JudgeJob, Long> {
    @EntityGraph(attributePaths = {"author", "problem"})
    Optional<JudgeJob> findWithAuthorById(Long id);

    boolean existsByProblemIdAndSubmissionTrue(Long problemId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update JudgeJob j set j.verdict = :next, j.updatedAt = :now where j.id = :id and j.verdict = :previous")
    int transition(Long id, Verdict previous, Verdict next, LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update JudgeJob j set j.verdict = :verdict, j.passedCount = :passed, j.runtimeMs = :runtime, "
        + "j.message = :message, j.snapshot = '', j.stdin = null, j.updatedAt = :now where j.id = :id and j.verdict = :previous")
    int finish(Long id, Verdict previous, Verdict verdict, int passed, long runtime, String message, LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update JudgeJob j set j.stdout = :stdout, j.stderr = :stderr, j.stdin = null where j.id = :id")
    void saveOutput(Long id, String stdout, String stderr);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from JudgeJob j where j.expiresAt is not null and j.expiresAt < :now")
    void deleteExpired(LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from JudgeJob j where j.problem.id = :problemId and j.submission = false")
    void deleteExecutions(Long problemId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update JudgeJob j set j.verdict = :failed, j.snapshot = '', j.stdin = null, j.message = :message, j.updatedAt = :now "
        + "where j.verdict = :queued or j.verdict = :running")
    void failInterrupted(Verdict queued, Verdict running, Verdict failed, String message, LocalDateTime now);

    // 잔디용: 내 제출 중 판정이 verdict(예: 정답)이고 from 이후에 낸 것의 "문제 ID, 제출 시각"만 가져온다
    @Query("""
        select j.problem.id as problemId, j.createdAt as createdAt
        from JudgeJob j
        where j.author.id = :userId
          and j.submission = true
          and j.verdict = :verdict
          and j.createdAt >= :from
        """)
    List<SolvedSubmission> findSolvedSubmissions(Long userId, Verdict verdict, LocalDateTime from);

    // 위 조회 결과 한 줄을 담는 그릇
    interface SolvedSubmission {
        Long getProblemId();

        LocalDateTime getCreatedAt();
    }
}

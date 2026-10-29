package com.example.cortebe.domain.judge.repository;

import com.example.cortebe.domain.judge.entity.JudgeJob;
import com.example.cortebe.domain.judge.runner.Verdict;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
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
    @Query("update JudgeJob j set j.verdict = :failed, j.snapshot = '', j.stdin = null, j.message = :message "
        + "where j.verdict = :queued or j.verdict = :running")
    void failInterrupted(Verdict queued, Verdict running, Verdict failed, String message);
}

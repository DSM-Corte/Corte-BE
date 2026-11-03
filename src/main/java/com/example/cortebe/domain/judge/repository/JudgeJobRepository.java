package com.example.cortebe.domain.judge.repository;

import com.example.cortebe.domain.judge.entity.JudgeJob;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.judge.runner.Verdict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // 코드 같은 큰 컬럼 없이 목록에 필요한 컬럼만 조회하고, 문제 제목은 join으로 한 번에 가져온다
    @Query(value = "select j.id as id, p.id as problemId, p.title as problemTitle, j.language as language, "
        + "j.verdict as verdict, j.passedCount as passedCount, j.totalCount as totalCount, j.runtimeMs as runtimeMs, "
        + "j.createdAt as createdAt from JudgeJob j join j.problem p "
        + "where j.author.id = :authorId and j.submission = true and (:problemId is null or p.id = :problemId)",
        countQuery = "select count(j) from JudgeJob j "
            + "where j.author.id = :authorId and j.submission = true and (:problemId is null or j.problem.id = :problemId)")
    Page<Summary> findSummaries(Long authorId, Long problemId, Pageable pageable);

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

    interface Summary {
        Long getId();
        Long getProblemId();
        String getProblemTitle();
        Language getLanguage();
        Verdict getVerdict();
        int getPassedCount();
        int getTotalCount();
        long getRuntimeMs();
        LocalDateTime getCreatedAt();
    }
}

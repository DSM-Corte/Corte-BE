package com.example.cortebe.domain.judge.repository;

import com.example.cortebe.domain.judge.entity.JudgeCaseSnapshot;
import com.example.cortebe.domain.judge.runner.Verdict;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface JudgeCaseSnapshotRepository extends JpaRepository<JudgeCaseSnapshot, Long> {
    // DB 안에서 복사해 큰 입력 전체가 접수 요청의 메모리에 올라오지 않게 한다.
    @Modifying
    @Query(value = """
        insert into judge_case_snapshots (job_id, case_order, input, expected_output, created_at, updated_at)
        select :jobId, row_number() over (order by position, id), input, expected_output,
               current_timestamp, current_timestamp
        from test_cases where problem_id = :problemId
        """, nativeQuery = true)
    int copy(Long jobId, Long problemId);

    @Query("select c.input as input, c.expectedOutput as expectedOutput from JudgeCaseSnapshot c "
        + "where c.job.id = :jobId order by c.caseOrder")
    List<Summary> findBatch(Long jobId, Pageable pageable);

    @Modifying
    @Query("delete from JudgeCaseSnapshot c where c.job.id = :jobId")
    void deleteAllByJobId(Long jobId);

    @Modifying
    @Query("delete from JudgeCaseSnapshot c where c.job.expiresAt is not null and c.job.expiresAt < :now")
    void deleteExpired(LocalDateTime now);

    @Modifying
    @Query("delete from JudgeCaseSnapshot c where c.job.verdict <> :queued and c.job.verdict <> :running")
    void deleteFinished(Verdict queued, Verdict running);

    interface Summary {
        String getInput();
        String getExpectedOutput();
    }
}

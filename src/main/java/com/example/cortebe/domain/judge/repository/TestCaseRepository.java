package com.example.cortebe.domain.judge.repository;

import com.example.cortebe.domain.judge.entity.TestCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {
    long countByProblemId(Long problemId);
    Optional<TestCase> findByIdAndProblemId(Long id, Long problemId);
    List<TestCase> findByProblemIdOrderByPositionAscIdAsc(Long problemId);

    @Query(value = "select t.id as id, t.input as input, t.expectedOutput as expectedOutput, "
        + "t.publicExample as publicExample, t.position as position from TestCase t "
        + "where t.problem.id = :problemId and (:admin = true or t.publicExample = true)",
        countQuery = "select count(t) from TestCase t where t.problem.id = :problemId and (:admin = true or t.publicExample = true)")
    Page<Summary> findVisible(Long problemId, boolean admin, Pageable pageable);

    interface Summary {
        Long getId();
        String getInput();
        String getExpectedOutput();
        boolean getPublicExample();
        int getPosition();
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TestCase t where t.problem.id = :problemId")
    void deleteAllByProblemId(Long problemId);
}

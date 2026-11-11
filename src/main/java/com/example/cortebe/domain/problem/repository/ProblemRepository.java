package com.example.cortebe.domain.problem.repository;

import com.example.cortebe.domain.judge.runner.Verdict;
import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.entity.Difficulty;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProblemRepository extends JpaRepository<Problem, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Problem p where p.id = :id")
    Optional<Problem> findLockedById(Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from Problem p where p.id = :id")
    Optional<Problem> findReadLockedById(Long id);

    Page<Summary> findAllBy(Pageable pageable);
    Page<Summary> findAllByCategory(String category, Pageable pageable);

    @Query(value = """
        select p.id as id, p.title as title, p.category as category, p.difficulty as difficulty
        from Problem p
        where (:category is null or p.category = :category) and :tag member of p.tags
        """, countQuery = """
        select count(p) from Problem p
        where (:category is null or p.category = :category) and :tag member of p.tags
        """)
    Page<Summary> findAllByTag(@Param("category") String category, @Param("tag") String tag, Pageable pageable);

    @Query("""
        select p.id as problemId, tag as tag
        from Problem p join p.tags tag
        where p.id in :problemIds
        order by p.id, tag
        """)
    List<TagRow> findTagsByProblemIds(@Param("problemIds") Collection<Long> problemIds);

    @Query("""
        select p.category as category, p.difficulty as difficulty,
            count(distinct case when j.id is not null then p.id end) as attemptedCount,
            count(distinct case when j.verdict = :accepted then p.id end) as solvedCount
        from Problem p
        left join JudgeJob j on j.problem = p and j.author.id = :userId
            and j.submission = true and j.verdict in :attemptVerdicts
        group by p.category, p.difficulty
        """)
    List<ProblemStats> findProblemStats(Long userId, Collection<Verdict> attemptVerdicts, Verdict accepted);

    interface ProblemStats {
        String getCategory();
        Difficulty getDifficulty();
        long getAttemptedCount();
        long getSolvedCount();
    }

    interface Summary {
        Long getId();
        String getTitle();
        String getCategory();
        Difficulty getDifficulty();
    }

    interface TagRow {
        Long getProblemId();
        String getTag();
    }
}

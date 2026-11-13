package com.example.cortebe.domain.review.repository;

import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.review.entity.ReviewRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ReviewRequestRepository extends JpaRepository<ReviewRequest, Long> {

    @EntityGraph(attributePaths = {"requester", "problem"})
    Optional<ReviewRequest> findWithRequesterById(Long id);

    // 닫힌 요청은 요청자 본인에게만 보인다
    @Query(value = "select r.id as id, p.id as problemId, p.title as problemTitle, u.id as requesterId, "
        + "u.nickname as requesterNickname, r.language as language, r.closed as closed, r.createdAt as createdAt "
        + "from ReviewRequest r join r.requester u join r.problem p "
        + "where (r.closed = false or u.id = :userId) and (:problemId is null or p.id = :problemId)",
        countQuery = "select count(r) from ReviewRequest r "
            + "where (r.closed = false or r.requester.id = :userId) and (:problemId is null or r.problem.id = :problemId)")
    Page<Summary> findSummaries(Long userId, Long problemId, Pageable pageable);

    @Query(value = """
        select r.id as id, p.id as problemId, p.title as problemTitle,
            r.closed as closed, r.createdAt as createdAt
        from ReviewRequest r join r.problem p
        where r.requester.id = :userId and (:closed is null or r.closed = :closed)
        """, countQuery = """
        select count(r) from ReviewRequest r
        where r.requester.id = :userId and (:closed is null or r.closed = :closed)
        """)
    Page<OwnSummary> findOwnSummaries(Long userId, Boolean closed, Pageable pageable);

    interface OwnSummary {
        Long getId();
        Long getProblemId();
        String getProblemTitle();
        boolean isClosed();
        LocalDateTime getCreatedAt();
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ReviewRequest r set r.closed = true, r.updatedAt = :now where r.id = :id and r.closed = false")
    int close(Long id, LocalDateTime now);

    interface Summary {
        Long getId();
        Long getProblemId();
        String getProblemTitle();
        Long getRequesterId();
        String getRequesterNickname();
        Language getLanguage();
        boolean isClosed();
        LocalDateTime getCreatedAt();
    }
}

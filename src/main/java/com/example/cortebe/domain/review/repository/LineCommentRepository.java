package com.example.cortebe.domain.review.repository;

import com.example.cortebe.domain.review.entity.LineComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LineCommentRepository extends JpaRepository<LineComment, Long> {

    Optional<LineComment> findByIdAndReviewRequestId(Long id, Long reviewRequestId);

    @EntityGraph(attributePaths = "author")
    Optional<LineComment> findWithAuthorByIdAndReviewRequestId(Long id, Long reviewRequestId);

    @EntityGraph(attributePaths = "author")
    List<LineComment> findByReviewRequestIdOrderByLineNumberAscIdAsc(Long reviewRequestId);

    long countByReviewRequestId(Long reviewRequestId);
}

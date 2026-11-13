package com.example.cortebe.domain.review.repository;

import com.example.cortebe.domain.review.entity.ReviewComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewCommentRepository extends JpaRepository<ReviewComment, Long> {

    @EntityGraph(attributePaths = "author")
    Optional<ReviewComment> findWithAuthorByIdAndReviewRequestId(Long id, Long reviewRequestId);

    @EntityGraph(attributePaths = "author")
    Page<ReviewComment> findByReviewRequestId(Long reviewRequestId, Pageable pageable);
}

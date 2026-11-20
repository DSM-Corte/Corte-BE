package com.example.cortebe.domain.aireview.repository;

import com.example.cortebe.domain.aireview.entity.AiReview;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiReviewRepository extends JpaRepository<AiReview, Long> {

    @EntityGraph(attributePaths = "submission")
    Optional<AiReview> findWithSubmissionBySubmissionId(Long submissionId);

    boolean existsBySubmissionId(Long submissionId);
}

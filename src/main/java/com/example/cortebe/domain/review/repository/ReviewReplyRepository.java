package com.example.cortebe.domain.review.repository;

import com.example.cortebe.domain.review.entity.ReviewReply;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewReplyRepository extends JpaRepository<ReviewReply, Long> {

    @EntityGraph(attributePaths = "author")
    Optional<ReviewReply> findWithAuthorByIdAndReviewRequestId(Long id, Long reviewRequestId);

    @EntityGraph(attributePaths = "author")
    List<ReviewReply> findByLineCommentIdInOrderByIdAsc(Collection<Long> lineCommentIds);

    @EntityGraph(attributePaths = "author")
    List<ReviewReply> findByReviewCommentIdInOrderByIdAsc(Collection<Long> reviewCommentIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ReviewReply r where r.lineComment.id = :lineCommentId")
    void deleteAllByLineCommentId(Long lineCommentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ReviewReply r where r.reviewComment.id = :reviewCommentId")
    void deleteAllByReviewCommentId(Long reviewCommentId);
}

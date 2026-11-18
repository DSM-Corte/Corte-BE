package com.example.cortebe.domain.review.repository;

import com.example.cortebe.domain.review.entity.ReviewReplyReaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewReplyReactionRepository extends JpaRepository<ReviewReplyReaction, Long> {

    Optional<ReviewReplyReaction> findByIdAndReplyId(Long id, Long replyId);

    List<ReviewReplyReaction> findByReplyIdInOrderByIdAsc(Collection<Long> replyIds);
}

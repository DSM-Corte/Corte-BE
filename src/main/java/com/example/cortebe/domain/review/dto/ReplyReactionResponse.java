package com.example.cortebe.domain.review.dto;

import com.example.cortebe.domain.review.entity.ReplyReactionType;
import com.example.cortebe.domain.review.entity.ReviewReplyReaction;
import lombok.Builder;

@Builder
public record ReplyReactionResponse(Long id, ReplyReactionType type, Long userId) {

    public static ReplyReactionResponse from(ReviewReplyReaction reaction) {
        return new ReplyReactionResponse(reaction.getId(), reaction.getType(), reaction.getAuthor().getId());
    }
}

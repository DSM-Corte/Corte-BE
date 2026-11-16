package com.example.cortebe.domain.review.dto;

import com.example.cortebe.domain.review.entity.ReplyReactionType;
import com.example.cortebe.domain.review.entity.ReviewReply;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Builder
public record ReplyResponse(
    Long id,
    String content,
    Long authorId,
    String author,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<ReplyReactionResponse> reactions,
    Map<ReplyReactionType, Long> reactionCounts
) {
    public static ReplyResponse from(ReviewReply reply) {
        return from(reply, List.of());
    }

    public static ReplyResponse from(ReviewReply reply, List<ReplyReactionResponse> reactions) {
        Map<ReplyReactionType, Long> counts = reactions.stream()
            .collect(Collectors.groupingBy(ReplyReactionResponse::type, Collectors.counting()));
        return ReplyResponse.builder()
            .id(reply.getId())
            .content(reply.getContent())
            .authorId(reply.getAuthor().getId())
            .author(reply.getAuthor().getNickname())
            .createdAt(reply.getCreatedAt())
            .updatedAt(reply.getUpdatedAt())
            .reactions(reactions)
            .reactionCounts(counts)
            .build();
    }
}

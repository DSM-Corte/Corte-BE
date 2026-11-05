package com.example.cortebe.domain.review.dto;

import com.example.cortebe.domain.review.entity.ReviewReply;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ReplyResponse(
    Long id,
    String content,
    Long authorId,
    String author,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static ReplyResponse from(ReviewReply reply) {
        return ReplyResponse.builder()
            .id(reply.getId())
            .content(reply.getContent())
            .authorId(reply.getAuthor().getId())
            .author(reply.getAuthor().getNickname())
            .createdAt(reply.getCreatedAt())
            .updatedAt(reply.getUpdatedAt())
            .build();
    }
}

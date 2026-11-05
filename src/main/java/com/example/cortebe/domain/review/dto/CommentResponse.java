package com.example.cortebe.domain.review.dto;

import com.example.cortebe.domain.review.entity.ReviewComment;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record CommentResponse(
    Long id,
    String content,
    Long authorId,
    String author,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<ReplyResponse> replies
) {
    public static CommentResponse from(ReviewComment comment, List<ReplyResponse> replies) {
        return CommentResponse.builder()
            .id(comment.getId())
            .content(comment.getContent())
            .authorId(comment.getAuthor().getId())
            .author(comment.getAuthor().getNickname())
            .createdAt(comment.getCreatedAt())
            .updatedAt(comment.getUpdatedAt())
            .replies(replies)
            .build();
    }
}

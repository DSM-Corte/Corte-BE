package com.example.cortebe.domain.review.dto;

import com.example.cortebe.domain.review.entity.LineComment;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record LineCommentResponse(
    Long id,
    int lineNumber,
    String content,
    String suggestedCode,
    Long authorId,
    String author,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<ReplyResponse> replies
) {
    public static LineCommentResponse from(LineComment comment, List<ReplyResponse> replies) {
        return LineCommentResponse.builder()
            .id(comment.getId())
            .lineNumber(comment.getLineNumber())
            .content(comment.getContent())
            .suggestedCode(comment.getSuggestedCode())
            .authorId(comment.getAuthor().getId())
            .author(comment.getAuthor().getNickname())
            .createdAt(comment.getCreatedAt())
            .updatedAt(comment.getUpdatedAt())
            .replies(replies)
            .build();
    }
}

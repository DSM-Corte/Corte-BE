package com.example.cortebe.domain.board.dto;

import com.example.cortebe.domain.board.entity.PostComment;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PostCommentResponse(
    Long id,
    String content,
    String author,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static PostCommentResponse from(PostComment comment) {
        return PostCommentResponse.builder()
            .id(comment.getId())
            .content(comment.getContent())
            .author(comment.getAuthor().getNickname())
            .createdAt(comment.getCreatedAt())
            .updatedAt(comment.getUpdatedAt())
            .build();
    }
}

package com.example.cortebe.domain.board.dto;

import com.example.cortebe.domain.board.entity.Post;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PostResponse(
    Long id,
    String title,
    String content,
    String author,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static PostResponse from(Post post) {
        return PostResponse.builder()
            .id(post.getId())
            .title(post.getTitle())
            .content(post.getContent())
            .author(post.getAuthor().getNickname())
            .createdAt(post.getCreatedAt())
            .updatedAt(post.getUpdatedAt())
            .build();
    }
}

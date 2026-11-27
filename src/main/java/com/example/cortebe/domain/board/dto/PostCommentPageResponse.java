package com.example.cortebe.domain.board.dto;

import com.example.cortebe.domain.board.repository.PostCommentRepository;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

public record PostCommentPageResponse(
    List<Summary> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public static PostCommentPageResponse from(Page<PostCommentRepository.Summary> comments) {
        return new PostCommentPageResponse(comments.getContent().stream().map(Summary::from).toList(),
            comments.getNumber(), comments.getSize(), comments.getTotalElements(), comments.getTotalPages());
    }

    public record Summary(Long id, String content, String author, Long authorId,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
        public static Summary from(PostCommentRepository.Summary comment) {
            return new Summary(comment.getId(), comment.getContent(), comment.getAuthorNickname(), comment.getAuthorId(),
                comment.getCreatedAt(), comment.getUpdatedAt());
        }
    }
}

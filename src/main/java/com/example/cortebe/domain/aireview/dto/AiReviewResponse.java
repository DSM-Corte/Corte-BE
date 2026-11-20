package com.example.cortebe.domain.aireview.dto;

import com.example.cortebe.domain.aireview.entity.AiReview;
import lombok.Builder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record AiReviewResponse(
    Long id,
    Long submissionId,
    String model,
    String summary,
    List<Comment> comments,
    Suggestion suggestion,
    LocalDateTime createdAt
) {
    public static AiReviewResponse from(AiReview review, ObjectMapper mapper) {
        return AiReviewResponse.builder()
            .id(review.getId())
            .submissionId(review.getSubmission().getId())
            .model(review.getModel())
            .summary(review.getSummary())
            .comments(mapper.readValue(review.getComments(), new TypeReference<List<Comment>>() { }))
            .suggestion(mapper.readValue(review.getSuggestion(), Suggestion.class))
            .createdAt(review.getCreatedAt())
            .build();
    }

    public record Comment(int line, String body) {
    }

    public record Suggestion(String title, String description, String code) {
    }
}

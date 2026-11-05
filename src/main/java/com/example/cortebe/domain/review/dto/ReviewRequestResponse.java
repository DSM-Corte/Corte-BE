package com.example.cortebe.domain.review.dto;

import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.review.entity.ReviewRequest;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ReviewRequestResponse(
    Long id,
    Long problemId,
    String problemTitle,
    Long requesterId,
    String requester,
    Language language,
    String code,
    boolean closed,
    LocalDateTime createdAt
) {
    public static ReviewRequestResponse from(ReviewRequest request) {
        return ReviewRequestResponse.builder()
            .id(request.getId())
            .problemId(request.getProblem().getId())
            .problemTitle(request.getProblem().getTitle())
            .requesterId(request.getRequester().getId())
            .requester(request.getRequester().getNickname())
            .language(request.getLanguage())
            .code(request.getCode())
            .closed(request.isClosed())
            .createdAt(request.getCreatedAt())
            .build();
    }
}

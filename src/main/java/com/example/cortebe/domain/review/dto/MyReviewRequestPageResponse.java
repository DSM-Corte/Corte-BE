package com.example.cortebe.domain.review.dto;

import com.example.cortebe.domain.review.repository.ReviewRequestRepository.OwnSummary;
import lombok.Builder;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record MyReviewRequestPageResponse(
    List<Summary> content, int page, int size, long totalElements, int totalPages
) {
    public static MyReviewRequestPageResponse from(Page<OwnSummary> requests) {
        return MyReviewRequestPageResponse.builder()
            .content(requests.map(Summary::from).getContent())
            .page(requests.getNumber())
            .size(requests.getSize())
            .totalElements(requests.getTotalElements())
            .totalPages(requests.getTotalPages())
            .build();
    }

    @Builder
    public record Summary(Long id, Long problemId, String problemTitle, Status status, LocalDateTime createdAt) {
        private static Summary from(OwnSummary request) {
            return Summary.builder()
                .id(request.getId())
                .problemId(request.getProblemId())
                .problemTitle(request.getProblemTitle())
                .status(request.isClosed() ? Status.CLOSED : Status.OPEN)
                .createdAt(request.getCreatedAt())
                .build();
        }
    }

    public enum Status {
        OPEN, CLOSED
    }
}

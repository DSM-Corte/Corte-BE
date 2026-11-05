package com.example.cortebe.domain.review.controller;

import com.example.cortebe.domain.review.dto.ApplyRequest;
import com.example.cortebe.domain.review.dto.ApplyResponse;
import com.example.cortebe.domain.review.dto.CreateReviewRequestRequest;
import com.example.cortebe.domain.review.dto.ReviewRequestPageResponse;
import com.example.cortebe.domain.review.dto.ReviewRequestResponse;
import com.example.cortebe.domain.review.service.ReviewRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/review-requests")
@RequiredArgsConstructor
public class ReviewRequestController {

    private final ReviewRequestService reviewRequestService;

    @PostMapping
    public ResponseEntity<ReviewRequestResponse> create(@AuthenticationPrincipal Long userId,
                                                        @Valid @RequestBody CreateReviewRequestRequest request) {
        ReviewRequestResponse response = reviewRequestService.create(userId, request);
        return ResponseEntity.created(URI.create("/api/review-requests/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public ReviewRequestResponse get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return reviewRequestService.get(userId, id);
    }

    @GetMapping
    public ReviewRequestPageResponse list(@AuthenticationPrincipal Long userId,
                                          @RequestParam(required = false) Long problemId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return reviewRequestService.list(userId, problemId, page, size);
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<Void> close(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        reviewRequestService.close(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/apply")
    public ApplyResponse apply(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                               @Valid @RequestBody ApplyRequest request) {
        return reviewRequestService.apply(userId, id, request);
    }
}

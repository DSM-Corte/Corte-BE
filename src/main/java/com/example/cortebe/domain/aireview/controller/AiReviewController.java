package com.example.cortebe.domain.aireview.controller;

import com.example.cortebe.domain.aireview.dto.AiReviewResponse;
import com.example.cortebe.domain.aireview.service.AiReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class AiReviewController {

    private final AiReviewService aiReviewService;

    @PostMapping("/api/submissions/{id}/ai-review")
    public ResponseEntity<AiReviewResponse> create(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        AiReviewResponse response = aiReviewService.create(userId, id);
        return ResponseEntity.created(URI.create("/api/submissions/" + id + "/ai-review")).body(response);
    }

    @GetMapping("/api/submissions/{id}/ai-review")
    public AiReviewResponse get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return aiReviewService.get(userId, id);
    }
}

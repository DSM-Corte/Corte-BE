package com.example.cortebe.domain.user.controller;

import com.example.cortebe.domain.review.dto.MyReviewRequestPageResponse;
import com.example.cortebe.domain.review.dto.ReviewRequestStatusFilter;
import com.example.cortebe.domain.review.service.ReviewRequestService;
import com.example.cortebe.domain.user.dto.GrassResponse;
import org.springframework.web.bind.annotation.RequestParam;
import com.example.cortebe.domain.user.service.GrassService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final GrassService grassService;
    private final ReviewRequestService reviewRequestService;

    @GetMapping("/me/review-requests")
    public MyReviewRequestPageResponse getMyReviewRequests(
        @AuthenticationPrincipal Long userId,
        @RequestParam(defaultValue = "ALL") ReviewRequestStatusFilter status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return reviewRequestService.listMine(userId, status, page, size);
    }

    @GetMapping("/me/grass")
    public List<GrassResponse> getMyGrass(@AuthenticationPrincipal Long userId) {
        return grassService.getMyGrass(userId);
    }
}

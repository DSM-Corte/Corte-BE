package com.example.cortebe.domain.user.controller;

import com.example.cortebe.domain.user.dto.GrassResponse;
import com.example.cortebe.domain.user.dto.ProblemStatsResponse;
import com.example.cortebe.domain.user.service.GrassService;
import com.example.cortebe.domain.user.service.ProblemStatsService;
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
    private final ProblemStatsService problemStatsService;

    @GetMapping("/me/problem-stats")
    public List<ProblemStatsResponse> getMyProblemStats(@AuthenticationPrincipal Long userId) {
        return problemStatsService.getMyProblemStats(userId);
    }

    @GetMapping("/me/grass")
    public List<GrassResponse> getMyGrass(@AuthenticationPrincipal Long userId) {
        return grassService.getMyGrass(userId);
    }
}

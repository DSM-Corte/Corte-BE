package com.example.cortebe.domain.user.controller;

import com.example.cortebe.domain.user.dto.GrassResponse;
import com.example.cortebe.domain.user.dto.UserStatsResponse;
import com.example.cortebe.domain.user.service.GrassService;
import com.example.cortebe.domain.user.service.UserStatsService;
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
    private final UserStatsService userStatsService;

    @GetMapping("/me/grass")
    public List<GrassResponse> getMyGrass(@AuthenticationPrincipal Long userId) {
        return grassService.getMyGrass(userId);
    }

    @GetMapping("/me/stats")
    public UserStatsResponse getMyStats(@AuthenticationPrincipal Long userId) {
        return userStatsService.getMyStats(userId);
    }
}

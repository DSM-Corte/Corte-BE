package com.example.cortebe.domain.leaderboard.controller;

import com.example.cortebe.domain.leaderboard.dto.LeaderboardPageResponse;
import com.example.cortebe.domain.leaderboard.dto.LeaderboardSearch;
import com.example.cortebe.domain.leaderboard.dto.LeaderboardSort;
import com.example.cortebe.domain.leaderboard.entity.UserTier;
import com.example.cortebe.domain.leaderboard.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    // 공개 경로라 토큰이 없으면 userId는 null이다
    @GetMapping
    public LeaderboardPageResponse list(@AuthenticationPrincipal Long userId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) UserTier minTier,
                                        @RequestParam(required = false) UserTier maxTier,
                                        @RequestParam(required = false) Integer minSolved,
                                        @RequestParam(required = false) Integer maxSolved,
                                        @RequestParam(defaultValue = "RANK") LeaderboardSort sort,
                                        @RequestParam(required = false) Sort.Direction direction) {
        LeaderboardSearch search = LeaderboardSearch.builder()
            .page(page)
            .size(size)
            .keyword(keyword)
            .minTier(minTier)
            .maxTier(maxTier)
            .minSolved(minSolved)
            .maxSolved(maxSolved)
            .sort(sort)
            .direction(direction)
            .build();
        return leaderboardService.list(userId, search);
    }
}

package com.example.cortebe.domain.leaderboard.service;

import com.example.cortebe.domain.judge.repository.JudgeJobRepository;
import com.example.cortebe.domain.judge.repository.JudgeJobRepository.LeaderboardRow;
import com.example.cortebe.domain.judge.runner.Verdict;
import com.example.cortebe.domain.leaderboard.dto.LeaderboardPageResponse;
import com.example.cortebe.domain.leaderboard.dto.LeaderboardPageResponse.Entry;
import com.example.cortebe.domain.leaderboard.dto.LeaderboardSearch;
import com.example.cortebe.domain.leaderboard.dto.LeaderboardSort;
import com.example.cortebe.domain.problem.entity.Difficulty;
import com.example.cortebe.domain.user.entity.UserTier;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.domain.user.exception.UserNotFoundException;
import com.example.cortebe.domain.user.repository.ProblemRewardRepository;
import com.example.cortebe.domain.user.repository.ProblemRewardRepository.RewardRow;
import com.example.cortebe.domain.user.service.UserTierService;
import com.example.cortebe.global.error.exception.InvalidInputException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaderboardService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    // 채점이 끝난 판정만 센다. 내 통계(UserStatsService)와 같은 기준이다
    private static final Set<Verdict> JUDGED_VERDICTS = EnumSet.of(Verdict.ACCEPTED, Verdict.WRONG_ANSWER,
        Verdict.COMPILE_ERROR, Verdict.RUNTIME_ERROR, Verdict.TIME_LIMIT, Verdict.MEMORY_LIMIT, Verdict.OUTPUT_LIMIT);

    private final JudgeJobRepository judgeJobRepository;
    private final ProblemRewardRepository problemRewardRepository;

    private final UserRepository userRepository;

    public Entry getMine(Long viewerId) {
        return rank(viewerId).stream().filter(Entry::me).findFirst()
            .orElseThrow(UserNotFoundException::new);
    }

    private List<Entry> rank(Long viewerId) {

        // 1. 회원마다 제출 기록을 모은다
        Map<Long, Player> players = new LinkedHashMap<>();
        Map<Long, LocalDateTime> joinedAt = new HashMap<>();
        for (var user : userRepository.findAll()) {
            players.put(user.getId(), new Player(user.getId(), user.getNickname()));
            joinedAt.put(user.getId(), user.getJoinedAt());
        }
        for (LeaderboardRow row : judgeJobRepository.findLeaderboardRows(JUDGED_VERDICTS)) {
            Player player = players.computeIfAbsent(row.getUserId(), id -> new Player(id, row.getNickname()));
            player.add(row);
        }

        // 2. 이미 지급된 점수 기록을 회원에게 붙인다(#74의 problem_rewards)
        for (RewardRow reward : problemRewardRepository.findAllRewardRows()) {
            Player player = players.get(reward.getUserId());
            if (player != null) {
                player.rewards.put(reward.getProblemId(), reward.getPoints());
            }
        }

        // 3. 제출 기록이 없는 회원도 순위표에 포함한다
        LocalDate today = LocalDate.now(KOREA);
        List<Entry> ranked = new ArrayList<>();
        for (Player player : players.values()) {
            ranked.add(player.toEntry(today, viewerId));
        }

        // 4. 점수 → 가입 시각 → 회원 ID 순으로 전체 순위를 매긴다
        ranked.sort(Comparator.comparingLong(Entry::score).reversed()
            .thenComparing(entry -> joinedAt.get(entry.userId()), Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(Entry::userId));
        List<Entry> withRank = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            withRank.add(withRank(ranked.get(i), i + 1));
        }

        return withRank;
    }

    public LeaderboardPageResponse list(Long viewerId, LeaderboardSearch search) {
        validate(search);
        List<Entry> withRank = rank(viewerId);

        // 5. 검색·필터는 순위를 매긴 뒤에 걸어서 순위 번호가 전체 기준을 유지한다
        List<Entry> filtered = new ArrayList<>();
        for (Entry entry : withRank) {
            if (matches(entry, search)) {
                filtered.add(entry);
            }
        }

        // 6. 정렬하고 한 페이지만 잘라 준다
        filtered.sort(comparator(search.sort(), search.direction()));
        int total = filtered.size();
        int from = (int) Math.min((long) search.page() * search.size(), total);
        int to = Math.min(from + search.size(), total);
        int totalPages = (total + search.size() - 1) / search.size();
        return new LeaderboardPageResponse(filtered.subList(from, to), search.page(), search.size(), total, totalPages);
    }

    private void validate(LeaderboardSearch search) {
        if (search.page() < 0 || search.size() < 1 || search.size() > MAX_PAGE_SIZE) {
            throw new InvalidInputException();
        }
        Integer minSolved = search.minSolved();
        Integer maxSolved = search.maxSolved();
        if ((minSolved != null && minSolved < 0) || (maxSolved != null && maxSolved < 0)) {
            throw new InvalidInputException();
        }
        if (minSolved != null && maxSolved != null && minSolved > maxSolved) {
            throw new InvalidInputException();
        }
        if (search.minTier() != null && search.maxTier() != null
            && search.minTier().compareTo(search.maxTier()) > 0) {
            throw new InvalidInputException();
        }
    }

    private boolean matches(Entry entry, LeaderboardSearch search) {
        String keyword = search.keyword() == null ? "" : search.keyword().trim().toLowerCase(Locale.ROOT);
        if (!keyword.isEmpty() && !entry.nickname().toLowerCase(Locale.ROOT).contains(keyword)) {
            return false;
        }
        if (search.minTier() != null && entry.tier().compareTo(search.minTier()) < 0) {
            return false;
        }
        if (search.maxTier() != null && entry.tier().compareTo(search.maxTier()) > 0) {
            return false;
        }
        if (search.minSolved() != null && entry.solvedCount() < search.minSolved()) {
            return false;
        }
        if (search.maxSolved() != null && entry.solvedCount() > search.maxSolved()) {
            return false;
        }
        return true;
    }

    // 정렬 값이 같으면 순위 오름차순으로 둔다
    private Comparator<Entry> comparator(LeaderboardSort sort, Sort.Direction direction) {
        LeaderboardSort key = sort == null ? LeaderboardSort.RANK : sort;
        Sort.Direction order = direction;
        if (order == null) {
            order = key == LeaderboardSort.RANK ? Sort.Direction.ASC : Sort.Direction.DESC;
        }
        Comparator<Entry> byKey = Comparator.comparingDouble(entry -> sortValue(entry, key));
        if (order == Sort.Direction.DESC) {
            byKey = byKey.reversed();
        }
        return byKey.thenComparingInt(Entry::rank);
    }

    private double sortValue(Entry entry, LeaderboardSort sort) {
        return switch (sort) {
            case RANK -> entry.rank();
            case SCORE -> entry.score();
            case SOLVED -> entry.solvedCount();
            case ACCURACY -> entry.accuracy();
            case STREAK -> entry.streak();
        };
    }

    private Entry withRank(Entry entry, int rank) {
        return Entry.builder()
            .rank(rank)
            .userId(entry.userId())
            .nickname(entry.nickname())
            .tier(entry.tier())
            .score(entry.score())
            .solvedCount(entry.solvedCount())
            .submissionCount(entry.submissionCount())
            .accuracy(entry.accuracy())
            .streak(entry.streak())
            .me(entry.me())
            .build();
    }

    // 한 회원의 제출 기록을 모으는 그릇
    private static class Player {
        private final Long userId;
        private final String nickname;
        private final Map<Long, Difficulty> solvedProblems = new HashMap<>();
        private final Set<LocalDate> solvedDates = new HashSet<>();
        // 문제 ID → 이미 지급된 점수
        private final Map<Long, Long> rewards = new HashMap<>();
        private long submissions;
        private long accepted;

        Player(Long userId, String nickname) {
            this.userId = userId;
            this.nickname = nickname;
        }

        void add(LeaderboardRow row) {
            submissions++;
            if (row.getVerdict() == Verdict.ACCEPTED) {
                accepted++;
                solvedProblems.put(row.getProblemId(), row.getDifficulty());
                solvedDates.add(toKoreanDate(row.getCreatedAt()));
            }
        }

        Entry toEntry(LocalDate today, Long viewerId) {
            // GET /api/users/me/tier의 score와 같은 값:
            // 지급 기록 합계 + 아직 기록이 없는 정답 문제의 현재 난이도 점수
            long score = 0;
            for (long points : rewards.values()) {
                score += points;
            }
            for (Long problemId : solvedProblems.keySet()) {
                if (!rewards.containsKey(problemId)) {
                    score += UserTierService.points(solvedProblems.get(problemId));
                }
            }
            return Entry.builder()
                .userId(userId)
                .nickname(nickname)
                .tier(UserTier.fromScore(score))
                .score(score)
                .solvedCount(solvedProblems.size())
                .submissionCount(submissions)
                .accuracy(submissions == 0 ? 0 : Math.round(accepted * 1000.0 / submissions) / 10.0)
                .streak(streak(today))
                .me(userId.equals(viewerId))
                .build();
        }

        // 오늘이나 어제에서 끝나는, 정답이 있는 날이 끊기지 않고 이어진 일수
        private int streak(LocalDate today) {
            LocalDate day = today;
            if (!solvedDates.contains(day)) {
                day = today.minusDays(1);
            }
            int count = 0;
            while (solvedDates.contains(day)) {
                count++;
                day = day.minusDays(1);
            }
            return count;
        }

        // DB의 시각은 서버 시간대로 저장돼서 한국 시간 날짜로 바꾼다(GrassService와 같은 방식)
        private static LocalDate toKoreanDate(LocalDateTime serverTime) {
            return serverTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(KOREA).toLocalDate();
        }
    }
}

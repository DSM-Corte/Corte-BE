package com.example.cortebe.domain.leaderboard.service;

import com.example.cortebe.domain.judge.repository.JudgeJobRepository;
import com.example.cortebe.domain.judge.repository.JudgeJobRepository.LeaderboardRow;
import com.example.cortebe.domain.judge.runner.Verdict;
import com.example.cortebe.domain.leaderboard.dto.LeaderboardPageResponse;
import com.example.cortebe.domain.leaderboard.dto.LeaderboardPageResponse.Entry;
import com.example.cortebe.domain.leaderboard.dto.LeaderboardSearch;
import com.example.cortebe.domain.leaderboard.dto.LeaderboardSort;
import com.example.cortebe.domain.leaderboard.entity.UserTier;
import com.example.cortebe.domain.problem.entity.Difficulty;
import com.example.cortebe.global.error.exception.InvalidInputException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTests {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final EnumSet<Verdict> JUDGED = EnumSet.of(Verdict.ACCEPTED, Verdict.WRONG_ANSWER,
        Verdict.COMPILE_ERROR, Verdict.RUNTIME_ERROR, Verdict.TIME_LIMIT, Verdict.MEMORY_LIMIT, Verdict.OUTPUT_LIMIT);

    @Mock
    private JudgeJobRepository judgeJobRepository;

    @InjectMocks
    private LeaderboardService leaderboardService;

    @Test
    void 채점이_끝난_7가지_판정만_조회한다() {
        // given
        given(judgeJobRepository.findLeaderboardRows(eq(JUDGED))).willReturn(List.of());

        // when
        LeaderboardPageResponse response = leaderboardService.list(null, search().build());

        // then
        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    void XP는_서로_다른_정답_문제마다_난이도_XP를_한_번씩_더한다() {
        // given
        rows(
            accepted(1L, "민수", 10L, Difficulty.BRONZE_5, 0),   // 1
            accepted(1L, "민수", 10L, Difficulty.BRONZE_5, 0),   // 같은 문제를 또 맞힘 → 더하지 않는다
            accepted(1L, "민수", 11L, Difficulty.SILVER_3, 0),   // 10
            accepted(1L, "민수", 12L, Difficulty.GOLD_1, 0),     // 36
            accepted(1L, "민수", 13L, Difficulty.PLATINUM_5, 0), // 45
            accepted(1L, "민수", 14L, Difficulty.DIAMOND_1, 0),  // 720
            accepted(1L, "민수", 15L, Difficulty.RUBY_5, 0),     // 900
            accepted(1L, "민수", 16L, Difficulty.RUBY_1, 0),     // 900
            accepted(1L, "민수", 17L, Difficulty.UNRATED, 0),    // 0
            wrong(1L, "민수", 18L, Difficulty.GOLD_1, 0)         // 오답은 XP 없음
        );

        // when
        Entry entry = leaderboardService.list(null, search().build()).content().getFirst();

        // then
        assertThat(entry.xp()).isEqualTo(2612); // 1 + 10 + 36 + 45 + 720 + 900 + 900
        assertThat(entry.solvedCount()).isEqualTo(8);
        assertThat(entry.tier()).isEqualTo(UserTier.GOLD_5); // Gold 5 기준 2400 이상, Gold 4 기준 3100 미만
    }

    @Test
    void 난이도별_XP는_rank_md_표를_따른다() {
        // given
        Map<Difficulty, Long> expected = new EnumMap<>(Difficulty.class);
        long[] values = {1, 2, 3, 4, 5, 7, 8, 10, 12, 14, 18, 22, 26, 31, 36, 45, 55, 67, 80, 95,
            310, 380, 470, 580, 720, 900, 900, 900, 900, 900, 0};
        Difficulty[] difficulties = Difficulty.values(); // BRONZE_5 … DIAMOND_1, RUBY_5 … RUBY_1, UNRATED
        for (int i = 0; i < difficulties.length; i++) {
            expected.put(difficulties[i], values[i]);
        }

        // when & then
        assertThat(difficulties).hasSize(values.length);
        for (Difficulty difficulty : difficulties) {
            assertThat(LeaderboardService.xpOf(difficulty)).as(difficulty.name()).isEqualTo(expected.get(difficulty));
        }
    }

    @Test
    void 정답_문제가_없는_회원은_순위표에_넣지_않는다() {
        // given
        rows(
            accepted(1L, "민수", 10L, Difficulty.BRONZE_5, 0),
            wrong(2L, "지수", 10L, Difficulty.BRONZE_5, 0)
        );

        // when
        LeaderboardPageResponse response = leaderboardService.list(null, search().build());

        // then
        assertThat(response.content()).extracting(Entry::userId).containsExactly(1L);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void 제출_수와_정답률은_채점이_끝난_제출_기준으로_센다() {
        // given
        rows(
            accepted(1L, "민수", 10L, Difficulty.BRONZE_5, 0),
            accepted(1L, "민수", 10L, Difficulty.BRONZE_5, 0),
            wrong(1L, "민수", 11L, Difficulty.BRONZE_5, 0)
        );

        // when
        Entry entry = leaderboardService.list(null, search().build()).content().getFirst();

        // then
        assertThat(entry.submissionCount()).isEqualTo(3);
        assertThat(entry.accuracy()).isEqualTo(66.7);
    }

    @Test
    void 연속_풀이는_오늘까지_이어진_정답_날짜_수다() {
        // given
        rows(
            accepted(1L, "민수", 10L, Difficulty.BRONZE_5, 0),
            accepted(1L, "민수", 11L, Difficulty.BRONZE_5, 1),
            accepted(1L, "민수", 12L, Difficulty.BRONZE_5, 2),
            accepted(1L, "민수", 13L, Difficulty.BRONZE_5, 4),  // 사흘 전은 끊겨서 세지 않는다
            wrong(1L, "민수", 14L, Difficulty.BRONZE_5, 3)      // 오답만 낸 날은 이어 주지 않는다
        );

        // when
        Entry entry = leaderboardService.list(null, search().build()).content().getFirst();

        // then
        assertThat(entry.streak()).isEqualTo(3);
    }

    @Test
    void 오늘_정답이_없어도_어제까지_이어졌으면_센다() {
        // given
        rows(
            accepted(1L, "민수", 10L, Difficulty.BRONZE_5, 1),
            accepted(1L, "민수", 11L, Difficulty.BRONZE_5, 2)
        );

        // when
        Entry entry = leaderboardService.list(null, search().build()).content().getFirst();

        // then
        assertThat(entry.streak()).isEqualTo(2);
    }

    @Test
    void 마지막_정답이_그제_이전이면_연속_풀이는_0이다() {
        // given
        rows(accepted(1L, "민수", 10L, Difficulty.BRONZE_5, 2));

        // when
        Entry entry = leaderboardService.list(null, search().build()).content().getFirst();

        // then
        assertThat(entry.streak()).isZero();
    }

    @Test
    void 연속_풀이_날짜는_한국_시간_자정을_기준으로_나눈다() {
        // given
        LocalDate today = LocalDate.now(KOREA);
        // 한국 시간 오늘 0시 1분과 어제 23시 59분은 서로 다른 날이다
        rows(
            row(1L, "민수", 10L, Difficulty.BRONZE_5, Verdict.ACCEPTED, koreanTime(today.atTime(0, 1))),
            row(1L, "민수", 11L, Difficulty.BRONZE_5, Verdict.ACCEPTED, koreanTime(today.minusDays(1).atTime(23, 59)))
        );

        // when
        Entry entry = leaderboardService.list(null, search().build()).content().getFirst();

        // then
        assertThat(entry.streak()).isEqualTo(2);
    }

    @Test
    void 순위는_XP_정답_수_회원_ID_순으로_매긴다() {
        // given
        rows(
            accepted(3L, "다", 10L, Difficulty.BRONZE_4, 0),  // 2XP, 1문제
            accepted(1L, "가", 10L, Difficulty.BRONZE_5, 0),  // 2XP, 2문제
            accepted(1L, "가", 11L, Difficulty.BRONZE_5, 0),
            accepted(2L, "나", 10L, Difficulty.BRONZE_4, 0),  // 2XP, 1문제, ID가 3보다 작다
            accepted(4L, "라", 10L, Difficulty.SILVER_5, 0)   // 7XP
        );

        // when
        LeaderboardPageResponse response = leaderboardService.list(null, search().build());

        // then
        assertThat(response.content()).extracting(Entry::rank, Entry::userId)
            .containsExactly(tuple(1, 4L), tuple(2, 1L), tuple(3, 2L), tuple(4, 3L));
    }

    @Test
    void 검색과_필터를_걸어도_순위_번호는_전체_기준을_유지한다() {
        // given
        rows(
            accepted(1L, "Alpha", 10L, Difficulty.SILVER_5, 0),
            accepted(2L, "beta", 10L, Difficulty.BRONZE_5, 0),
            accepted(3L, "ALPHABET", 10L, Difficulty.BRONZE_4, 0)
        );

        // when
        LeaderboardPageResponse response = leaderboardService.list(null, search().keyword(" alpha ").build());

        // then
        assertThat(response.content()).extracting(Entry::rank, Entry::nickname)
            .containsExactly(tuple(1, "Alpha"), tuple(2, "ALPHABET"));
        assertThat(response.totalElements()).isEqualTo(2);
    }

    @Test
    void 티어와_정답_수_범위는_양_끝을_포함한다() {
        // given
        List<LeaderboardRow> rows = new ArrayList<>();
        rows.add(accepted(1L, "브론즈5", 10L, Difficulty.GOLD_5, 0));     // 18XP → BRONZE_5
        for (long problemId = 20; problemId < 23; problemId++) {
            rows.add(accepted(2L, "브론즈4", problemId, Difficulty.GOLD_4, 0)); // 66XP, 3문제 → BRONZE_4
        }
        for (long problemId = 30; problemId < 35; problemId++) {
            rows.add(accepted(3L, "브론즈3", problemId, Difficulty.GOLD_3, 0)); // 130XP, 5문제 → BRONZE_3
        }
        given(judgeJobRepository.findLeaderboardRows(eq(JUDGED))).willReturn(rows);

        // when
        LeaderboardPageResponse byTier = leaderboardService.list(null,
            search().minTier(UserTier.BRONZE_4).maxTier(UserTier.BRONZE_3).build());
        LeaderboardPageResponse bySolved = leaderboardService.list(null,
            search().minSolved(1).maxSolved(3).build());

        // then
        assertThat(byTier.content()).extracting(Entry::userId).containsExactly(3L, 2L);
        assertThat(bySolved.content()).extracting(Entry::userId).containsExactly(2L, 1L);
    }

    @Test
    void 정렬_방향이_없으면_순위는_오름차순_나머지는_내림차순이다() {
        // given
        rows(
            accepted(1L, "가", 10L, Difficulty.SILVER_5, 0),
            accepted(2L, "나", 10L, Difficulty.BRONZE_5, 0),
            wrong(2L, "나", 10L, Difficulty.BRONZE_5, 0)
        );

        // when
        LeaderboardPageResponse byRank = leaderboardService.list(null, search().build());
        LeaderboardPageResponse byAccuracy = leaderboardService.list(null,
            search().sort(LeaderboardSort.ACCURACY).build());
        LeaderboardPageResponse byXpAsc = leaderboardService.list(null,
            search().sort(LeaderboardSort.XP).direction(Sort.Direction.ASC).build());
        LeaderboardPageResponse byRankDesc = leaderboardService.list(null,
            search().direction(Sort.Direction.DESC).build());

        // then
        assertThat(byRank.content()).extracting(Entry::userId).containsExactly(1L, 2L);
        assertThat(byAccuracy.content()).extracting(Entry::userId).containsExactly(1L, 2L);
        assertThat(byXpAsc.content()).extracting(Entry::userId).containsExactly(2L, 1L);
        assertThat(byRankDesc.content()).extracting(Entry::userId).containsExactly(2L, 1L);
    }

    @Test
    void 정렬_기준마다_그_값으로_정렬한다() {
        // given: 기준마다 순위와 다른 순서가 나오도록 만든다
        rows(
            // 1번: 3XP, 3문제, 정답률 100, 연속 1일
            accepted(1L, "가", 20L, Difficulty.BRONZE_5, 0),
            accepted(1L, "가", 21L, Difficulty.BRONZE_5, 0),
            accepted(1L, "가", 22L, Difficulty.BRONZE_5, 0),
            // 2번: 7XP, 1문제, 정답률 50, 연속 3일
            accepted(2L, "나", 10L, Difficulty.SILVER_5, 0),
            accepted(2L, "나", 10L, Difficulty.SILVER_5, 1),
            accepted(2L, "나", 10L, Difficulty.SILVER_5, 2),
            wrong(2L, "나", 10L, Difficulty.SILVER_5, 0),
            wrong(2L, "나", 10L, Difficulty.SILVER_5, 0),
            wrong(2L, "나", 10L, Difficulty.SILVER_5, 0),
            // 3번: 2XP, 1문제, 정답률 100, 연속 2일
            accepted(3L, "다", 30L, Difficulty.BRONZE_4, 0),
            accepted(3L, "다", 30L, Difficulty.BRONZE_4, 1)
        );

        // when
        LeaderboardPageResponse byRank = leaderboardService.list(null, search().build());
        LeaderboardPageResponse byXpAsc = leaderboardService.list(null,
            search().sort(LeaderboardSort.XP).direction(Sort.Direction.ASC).build());
        LeaderboardPageResponse bySolved = leaderboardService.list(null, search().sort(LeaderboardSort.SOLVED).build());
        LeaderboardPageResponse byAccuracy = leaderboardService.list(null,
            search().sort(LeaderboardSort.ACCURACY).build());
        LeaderboardPageResponse byStreak = leaderboardService.list(null, search().sort(LeaderboardSort.STREAK).build());

        // then
        assertThat(byRank.content()).extracting(Entry::userId).containsExactly(2L, 1L, 3L);
        assertThat(byXpAsc.content()).extracting(Entry::userId).containsExactly(3L, 1L, 2L);
        assertThat(bySolved.content()).extracting(Entry::userId).containsExactly(1L, 2L, 3L);
        assertThat(byAccuracy.content()).extracting(Entry::userId).containsExactly(1L, 3L, 2L);
        assertThat(byStreak.content()).extracting(Entry::userId).containsExactly(2L, 3L, 1L);
    }

    @Test
    void 정렬_값이_같으면_순위_오름차순으로_둔다() {
        // given
        rows(
            accepted(1L, "가", 10L, Difficulty.SILVER_5, 0),
            accepted(2L, "나", 10L, Difficulty.BRONZE_5, 0)
        );

        // when
        LeaderboardPageResponse bySolvedAsc = leaderboardService.list(null,
            search().sort(LeaderboardSort.SOLVED).direction(Sort.Direction.ASC).build());
        LeaderboardPageResponse byStreak = leaderboardService.list(null,
            search().sort(LeaderboardSort.STREAK).build());

        // then
        assertThat(bySolvedAsc.content()).extracting(Entry::userId).containsExactly(1L, 2L);
        assertThat(byStreak.content()).extracting(Entry::userId).containsExactly(1L, 2L);
    }

    @Test
    void 페이지를_잘라서_돌려준다() {
        // given
        rows(
            accepted(1L, "가", 10L, Difficulty.GOLD_5, 0),
            accepted(2L, "나", 10L, Difficulty.SILVER_5, 0),
            accepted(3L, "다", 10L, Difficulty.BRONZE_5, 0)
        );

        // when
        LeaderboardPageResponse response = leaderboardService.list(null, search().page(1).size(2).build());

        // then
        assertThat(response.content()).extracting(Entry::rank).containsExactly(3);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(2);
    }

    @Test
    void 로그인한_회원의_항목에만_me를_표시한다() {
        // given
        rows(
            accepted(1L, "가", 10L, Difficulty.GOLD_5, 0),
            accepted(2L, "나", 10L, Difficulty.SILVER_5, 0)
        );

        // when
        LeaderboardPageResponse response = leaderboardService.list(2L, search().build());

        // then
        assertThat(response.content()).extracting(Entry::userId, Entry::me)
            .containsExactly(tuple(1L, false), tuple(2L, true));
    }

    @Test
    void 페이지_범위를_벗어나면_조회에_실패한다() {
        // when & then
        assertThatThrownBy(() -> leaderboardService.list(null, search().page(-1).build()))
            .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> leaderboardService.list(null, search().size(0).build()))
            .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> leaderboardService.list(null, search().size(101).build()))
            .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void 정답_수_범위가_잘못되면_조회에_실패한다() {
        // when & then
        assertThatThrownBy(() -> leaderboardService.list(null, search().minSolved(-1).build()))
            .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> leaderboardService.list(null, search().maxSolved(-1).build()))
            .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> leaderboardService.list(null, search().minSolved(5).maxSolved(4).build()))
            .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void 최소_티어가_최대_티어보다_높으면_조회에_실패한다() {
        // when & then
        assertThatThrownBy(() -> leaderboardService.list(null,
                search().minTier(UserTier.GOLD_5).maxTier(UserTier.SILVER_1).build()))
            .isInstanceOf(InvalidInputException.class);
    }

    private LeaderboardSearch.LeaderboardSearchBuilder search() {
        return LeaderboardSearch.builder().page(0).size(20).sort(LeaderboardSort.RANK);
    }

    private void rows(LeaderboardRow... rows) {
        given(judgeJobRepository.findLeaderboardRows(eq(JUDGED))).willReturn(List.of(rows));
    }

    // daysAgo: 한국 시간 기준 며칠 전 정오에 낸 제출
    private LeaderboardRow accepted(Long userId, String nickname, Long problemId, Difficulty difficulty, int daysAgo) {
        return row(userId, nickname, problemId, difficulty, Verdict.ACCEPTED, noonDaysAgo(daysAgo));
    }

    private LeaderboardRow wrong(Long userId, String nickname, Long problemId, Difficulty difficulty, int daysAgo) {
        return row(userId, nickname, problemId, difficulty, Verdict.WRONG_ANSWER, noonDaysAgo(daysAgo));
    }

    private LocalDateTime noonDaysAgo(int daysAgo) {
        return koreanTime(LocalDate.now(KOREA).minusDays(daysAgo).atTime(12, 0));
    }

    // 한국 시간을 DB에 저장되는 서버 시간대 시각으로 바꾼다
    private LocalDateTime koreanTime(LocalDateTime koreanTime) {
        return koreanTime.atZone(KOREA).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    private LeaderboardRow row(Long userId, String nickname, Long problemId, Difficulty difficulty,
                               Verdict verdict, LocalDateTime createdAt) {
        return new TestRow(userId, nickname, problemId, difficulty, verdict, createdAt);
    }

    private record TestRow(Long userId, String nickname, Long problemId, Difficulty difficulty,
                           Verdict verdict, LocalDateTime createdAt) implements LeaderboardRow {
        @Override
        public Long getUserId() {
            return userId;
        }

        @Override
        public String getNickname() {
            return nickname;
        }

        @Override
        public Long getProblemId() {
            return problemId;
        }

        @Override
        public Difficulty getDifficulty() {
            return difficulty;
        }

        @Override
        public Verdict getVerdict() {
            return verdict;
        }

        @Override
        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
    }
}

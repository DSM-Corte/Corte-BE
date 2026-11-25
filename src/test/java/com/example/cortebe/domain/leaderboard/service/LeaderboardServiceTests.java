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
import com.example.cortebe.domain.user.repository.ProblemRewardRepository;
import com.example.cortebe.domain.user.repository.ProblemRewardRepository.RewardRow;
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
import java.util.EnumSet;
import java.util.List;

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

    @Mock
    private ProblemRewardRepository problemRewardRepository;

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
    void 점수는_지급_기록_합계에_기록이_없는_정답_문제의_현재_난이도_점수를_더한다() {
        // given
        rows(
            accepted(1L, "민수", 10L, Difficulty.GOLD_1, 0),     // 기록 20점이 있다 → 현재 난이도(골드 30)가 아니라 20
            accepted(1L, "민수", 11L, Difficulty.SILVER_3, 0),   // 기록 없음 → 실버 20
            accepted(1L, "민수", 11L, Difficulty.SILVER_3, 0),   // 같은 문제를 또 맞힘 → 더하지 않는다
            accepted(1L, "민수", 12L, Difficulty.RUBY_1, 0),     // 기록 없음 → 루비 60
            accepted(1L, "민수", 13L, Difficulty.UNRATED, 0),    // 기록 없음 → 0
            wrong(1L, "민수", 14L, Difficulty.GOLD_1, 0)         // 오답은 점수 없음
        );
        rewards(reward(1L, 10L, 20L), reward(1L, 99L, 10L));    // 99번처럼 제출 행이 없는 기록도 합계에 넣는다

        // when
        Entry entry = leaderboardService.list(null, search().build()).content().getFirst();

        // then
        assertThat(entry.score()).isEqualTo(110); // 20 + 10 + 20 + 60 + 0
        assertThat(entry.solvedCount()).isEqualTo(4);
        assertThat(entry.tier()).isEqualTo(UserTier.SILVER_3); // 실버 3 기준 100 이상, 실버 2 기준 160 미만
    }

    @Test
    void 영점_지급_기록이_있으면_지금_난이도가_매겨져_있어도_영점이다() {
        // given: 미지정일 때 맞혀 0점 기록이 남은 문제가 나중에 골드가 됐다(#74는 추가 지급하지 않는다)
        rows(accepted(1L, "가", 10L, Difficulty.GOLD_1, 0));
        rewards(reward(1L, 10L, 0L));

        // when
        Entry entry = leaderboardService.list(null, search().build()).content().getFirst();

        // then
        assertThat(entry.score()).isZero();
        assertThat(entry.solvedCount()).isEqualTo(1);
    }

    @Test
    void 채점된_제출이_없는_회원의_지급_기록은_무시한다() {
        // given
        rows(accepted(1L, "가", 10L, Difficulty.BRONZE_5, 0));
        rewards(reward(2L, 10L, 30L));

        // when
        LeaderboardPageResponse response = leaderboardService.list(null, search().build());

        // then
        assertThat(response.content()).extracting(Entry::userId).containsExactly(1L);
    }

    @Test
    void 다른_회원의_지급_기록은_섞지_않는다() {
        // given
        rows(
            accepted(1L, "가", 10L, Difficulty.BRONZE_5, 0),
            accepted(2L, "나", 10L, Difficulty.BRONZE_5, 0)
        );
        rewards(reward(2L, 10L, 50L));

        // when
        LeaderboardPageResponse response = leaderboardService.list(null, search().build());

        // then
        assertThat(response.content()).extracting(Entry::userId, Entry::score)
            .containsExactly(tuple(2L, 50L), tuple(1L, 10L));
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
    void 순위는_점수_정답_수_회원_ID_순으로_매긴다() {
        // given
        rows(
            accepted(3L, "다", 10L, Difficulty.SILVER_4, 0),  // 20점, 1문제
            accepted(1L, "가", 10L, Difficulty.BRONZE_5, 0),  // 20점, 2문제
            accepted(1L, "가", 11L, Difficulty.BRONZE_5, 0),
            accepted(2L, "나", 10L, Difficulty.SILVER_4, 0),  // 20점, 1문제, ID가 3보다 작다
            accepted(4L, "라", 10L, Difficulty.GOLD_5, 0)     // 30점
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
            accepted(2L, "beta", 11L, Difficulty.UNRATED, 0),
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
        rows.add(accepted(1L, "브론즈2", 10L, Difficulty.GOLD_5, 0));     // 30점 → BRONZE_2
        for (long problemId = 20; problemId < 23; problemId++) {
            rows.add(accepted(2L, "브론즈1", problemId, Difficulty.GOLD_4, 0)); // 90점, 3문제 → BRONZE_1
        }
        for (long problemId = 30; problemId < 35; problemId++) {
            rows.add(accepted(3L, "실버3", problemId, Difficulty.GOLD_3, 0)); // 150점, 5문제 → SILVER_3
        }
        given(judgeJobRepository.findLeaderboardRows(eq(JUDGED))).willReturn(rows);

        // when
        LeaderboardPageResponse byTier = leaderboardService.list(null,
            search().minTier(UserTier.BRONZE_1).maxTier(UserTier.SILVER_3).build());
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
        LeaderboardPageResponse byScoreAsc = leaderboardService.list(null,
            search().sort(LeaderboardSort.SCORE).direction(Sort.Direction.ASC).build());
        LeaderboardPageResponse byRankDesc = leaderboardService.list(null,
            search().direction(Sort.Direction.DESC).build());

        // then
        assertThat(byRank.content()).extracting(Entry::userId).containsExactly(1L, 2L);
        assertThat(byAccuracy.content()).extracting(Entry::userId).containsExactly(1L, 2L);
        assertThat(byScoreAsc.content()).extracting(Entry::userId).containsExactly(2L, 1L);
        assertThat(byRankDesc.content()).extracting(Entry::userId).containsExactly(2L, 1L);
    }

    @Test
    void 정렬_기준마다_그_값으로_정렬한다() {
        // given: 기준마다 순위와 다른 순서가 나오도록 만든다
        rows(
            // 1번: 30점, 3문제, 정답률 100, 연속 1일
            accepted(1L, "가", 20L, Difficulty.BRONZE_5, 0),
            accepted(1L, "가", 21L, Difficulty.BRONZE_5, 0),
            accepted(1L, "가", 22L, Difficulty.BRONZE_5, 0),
            // 2번: 50점, 1문제, 정답률 50, 연속 3일
            accepted(2L, "나", 10L, Difficulty.DIAMOND_5, 0),
            accepted(2L, "나", 10L, Difficulty.DIAMOND_5, 1),
            accepted(2L, "나", 10L, Difficulty.DIAMOND_5, 2),
            wrong(2L, "나", 10L, Difficulty.DIAMOND_5, 0),
            wrong(2L, "나", 10L, Difficulty.DIAMOND_5, 0),
            wrong(2L, "나", 10L, Difficulty.DIAMOND_5, 0),
            // 3번: 10점, 1문제, 정답률 100, 연속 2일
            accepted(3L, "다", 30L, Difficulty.BRONZE_4, 0),
            accepted(3L, "다", 30L, Difficulty.BRONZE_4, 1)
        );

        // when
        LeaderboardPageResponse byRank = leaderboardService.list(null, search().build());
        LeaderboardPageResponse byScoreAsc = leaderboardService.list(null,
            search().sort(LeaderboardSort.SCORE).direction(Sort.Direction.ASC).build());
        LeaderboardPageResponse bySolved = leaderboardService.list(null, search().sort(LeaderboardSort.SOLVED).build());
        LeaderboardPageResponse byAccuracy = leaderboardService.list(null,
            search().sort(LeaderboardSort.ACCURACY).build());
        LeaderboardPageResponse byStreak = leaderboardService.list(null, search().sort(LeaderboardSort.STREAK).build());

        // then
        assertThat(byRank.content()).extracting(Entry::userId).containsExactly(2L, 1L, 3L);
        assertThat(byScoreAsc.content()).extracting(Entry::userId).containsExactly(3L, 1L, 2L);
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
                search().minTier(UserTier.GOLD_3).maxTier(UserTier.SILVER_1).build()))
            .isInstanceOf(InvalidInputException.class);
    }

    private LeaderboardSearch.LeaderboardSearchBuilder search() {
        return LeaderboardSearch.builder().page(0).size(20).sort(LeaderboardSort.RANK);
    }

    private void rows(LeaderboardRow... rows) {
        given(judgeJobRepository.findLeaderboardRows(eq(JUDGED))).willReturn(List.of(rows));
    }

    private void rewards(RewardRow... rows) {
        given(problemRewardRepository.findAllRewardRows()).willReturn(List.of(rows));
    }

    private RewardRow reward(Long userId, Long problemId, Long points) {
        return new RewardRow() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public Long getProblemId() {
                return problemId;
            }

            @Override
            public Long getPoints() {
                return points;
            }
        };
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

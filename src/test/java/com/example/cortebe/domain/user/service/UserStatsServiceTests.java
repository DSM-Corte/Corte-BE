package com.example.cortebe.domain.user.service;

import com.example.cortebe.domain.judge.repository.JudgeJobRepository;
import com.example.cortebe.domain.judge.repository.JudgeJobRepository.StatRow;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.judge.runner.Verdict;
import com.example.cortebe.domain.problem.entity.Difficulty;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.domain.problem.repository.ProblemRepository.TagRow;
import com.example.cortebe.domain.user.dto.UserStatsResponse;
import com.example.cortebe.domain.user.dto.UserStatsResponse.DifficultyCount;
import com.example.cortebe.domain.user.dto.UserStatsResponse.Group;
import com.example.cortebe.domain.user.dto.UserStatsResponse.HardestProblem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserStatsServiceTests {

    private static final EnumSet<Verdict> JUDGED = EnumSet.of(Verdict.ACCEPTED, Verdict.WRONG_ANSWER,
        Verdict.COMPILE_ERROR, Verdict.RUNTIME_ERROR, Verdict.TIME_LIMIT, Verdict.MEMORY_LIMIT, Verdict.OUTPUT_LIMIT);

    @Mock
    private JudgeJobRepository judgeJobRepository;

    @Mock
    private ProblemRepository problemRepository;

    @InjectMocks
    private UserStatsService userStatsService;

    @Test
    void 채점이_끝난_7가지_판정만_조회하고_푼_문제는_중복_없이_센다() {
        // given
        given(judgeJobRepository.findStatRows(eq(1L), eq(JUDGED))).willReturn(List.of(
            row(1L, Language.JAVA, Verdict.ACCEPTED),
            row(1L, Language.JAVA, Verdict.ACCEPTED),   // 같은 문제를 또 맞힘 → 푼 문제는 1개
            row(2L, Language.JAVA, Verdict.WRONG_ANSWER)
        ));
        given(problemRepository.findTagsByProblemIds(anyCollection())).willReturn(List.of());

        // when
        UserStatsResponse stats = userStatsService.getMyStats(1L);

        // then
        assertThat(stats.solvedCount()).isEqualTo(1);
        assertThat(stats.submissionCount()).isEqualTo(3);
        assertThat(stats.acceptedCount()).isEqualTo(2);
        assertThat(stats.accuracy()).isEqualTo(66.7);
        assertThat(stats.verdicts()).containsExactly(
            entry(Verdict.ACCEPTED, 2L), entry(Verdict.WRONG_ANSWER, 1L), entry(Verdict.COMPILE_ERROR, 0L),
            entry(Verdict.RUNTIME_ERROR, 0L), entry(Verdict.TIME_LIMIT, 0L), entry(Verdict.MEMORY_LIMIT, 0L),
            entry(Verdict.OUTPUT_LIMIT, 0L));
    }

    @Test
    void 제출이_없으면_0으로_채우고_태그를_조회하지_않는다() {
        // given
        given(judgeJobRepository.findStatRows(eq(1L), any())).willReturn(List.of());

        // when
        UserStatsResponse stats = userStatsService.getMyStats(1L);

        // then
        assertThat(stats.solvedCount()).isZero();
        assertThat(stats.submissionCount()).isZero();
        assertThat(stats.accuracy()).isEqualTo(0.0);
        assertThat(stats.verdicts()).hasSize(7).allSatisfy((verdict, count) -> assertThat(count).isZero());
        assertThat(stats.difficulties()).hasSize(31).allSatisfy(d -> assertThat(d.solvedCount()).isZero());
        assertThat(stats.languages()).isEmpty();
        assertThat(stats.tags()).isEmpty();
        assertThat(stats.categories()).isEmpty();
        assertThat(stats.hardestProblem()).isNull();
        then(problemRepository).should(never()).findTagsByProblemIds(anyCollection());
    }

    @Test
    void 언어별로_집계해_푼_문제_수_제출_수_이름_순으로_정렬한다() {
        // given
        given(judgeJobRepository.findStatRows(eq(1L), any())).willReturn(List.of(
            row(1L, Language.JAVA, Verdict.ACCEPTED),
            row(4L, Language.JAVA, Verdict.ACCEPTED),          // JAVA: 푼 문제 2, 제출 2
            row(1L, Language.PYTHON, Verdict.WRONG_ANSWER),
            row(1L, Language.PYTHON, Verdict.WRONG_ANSWER),
            row(2L, Language.PYTHON, Verdict.ACCEPTED),        // PYTHON: 푼 문제 1, 제출 3
            row(3L, Language.CPP, Verdict.ACCEPTED),           // CPP, C: 푼 문제 1, 제출 1 → 이름 순
            row(3L, Language.C, Verdict.ACCEPTED)
        ));
        given(problemRepository.findTagsByProblemIds(anyCollection())).willReturn(List.of());

        // when
        UserStatsResponse stats = userStatsService.getMyStats(1L);

        // then
        assertThat(stats.languages()).containsExactly(
            new Group("JAVA", 2, 2, 2, 100.0),
            new Group("PYTHON", 1, 3, 1, 33.3),
            new Group("C", 1, 1, 1, 100.0),
            new Group("CPP", 1, 1, 1, 100.0));
    }

    @Test
    void 태그는_문제의_모든_태그에_세고_분류도_같은_방식으로_집계한다() {
        // given
        given(judgeJobRepository.findStatRows(eq(1L), any())).willReturn(List.of(
            row(1L, "기초", Difficulty.UNRATED, Verdict.ACCEPTED),
            row(2L, "기초", Difficulty.UNRATED, Verdict.WRONG_ANSWER),
            row(3L, "심화", Difficulty.UNRATED, Verdict.ACCEPTED)   // 태그 없는 문제
        ));
        // 못 푼 문제(2)까지 시도한 문제 ID 전체로 태그를 조회한다
        given(problemRepository.findTagsByProblemIds(Set.of(1L, 2L, 3L))).willReturn(List.of(
            tag(1L, "구현"), tag(1L, "수학"), tag(2L, "수학")));

        // when
        UserStatsResponse stats = userStatsService.getMyStats(1L);

        // then
        assertThat(stats.tags()).containsExactly(
            new Group("수학", 1, 2, 1, 50.0),
            new Group("구현", 1, 1, 1, 100.0));
        assertThat(stats.categories()).containsExactly(
            new Group("기초", 1, 2, 1, 50.0),
            new Group("심화", 1, 1, 1, 100.0));
    }

    @Test
    void 난이도별_푼_문제_수와_UNRATED를_뺀_가장_어려운_문제를_구한다() {
        // given
        given(judgeJobRepository.findStatRows(eq(1L), any())).willReturn(List.of(
            row(2L, "기초", Difficulty.GOLD_3, Verdict.ACCEPTED),
            row(1L, "기초", Difficulty.GOLD_3, Verdict.ACCEPTED),
            row(3L, "기초", Difficulty.RUBY_1, Verdict.WRONG_ANSWER),  // 못 푼 문제
            row(4L, "기초", Difficulty.UNRATED, Verdict.ACCEPTED)
        ));
        given(problemRepository.findTagsByProblemIds(anyCollection())).willReturn(List.of());

        // when
        UserStatsResponse stats = userStatsService.getMyStats(1L);

        // then
        assertThat(stats.difficulties()).hasSize(31);
        assertThat(stats.difficulties().getFirst().difficulty()).isEqualTo(Difficulty.BRONZE_5);
        assertThat(stats.difficulties()).filteredOn(d -> d.solvedCount() > 0)
            .extracting(DifficultyCount::difficulty, DifficultyCount::solvedCount)
            .containsExactly(tuple(Difficulty.GOLD_3, 2L),
                tuple(Difficulty.UNRATED, 1L));
        assertThat(stats.hardestProblem()).isEqualTo(new HardestProblem(1L, "문제1", Difficulty.GOLD_3));
    }

    @Test
    void UNRATED_문제만_풀었으면_가장_어려운_문제는_없다() {
        // given
        given(judgeJobRepository.findStatRows(eq(1L), any())).willReturn(List.of(
            row(1L, "기초", Difficulty.UNRATED, Verdict.ACCEPTED)));
        given(problemRepository.findTagsByProblemIds(anyCollection())).willReturn(List.of());

        // when
        UserStatsResponse stats = userStatsService.getMyStats(1L);

        // then
        assertThat(stats.hardestProblem()).isNull();
    }

    private static Map.Entry<Verdict, Long> entry(Verdict verdict, long count) {
        return Map.entry(verdict, count);
    }

    private StatRow row(Long problemId, Language language, Verdict verdict) {
        return row(problemId, "기초", Difficulty.UNRATED, language, verdict);
    }

    private StatRow row(Long problemId, String category, Difficulty difficulty, Verdict verdict) {
        return row(problemId, category, difficulty, Language.JAVA, verdict);
    }

    private StatRow row(Long problemId, String category, Difficulty difficulty, Language language, Verdict verdict) {
        return new StatRow() {
            public Long getProblemId() {
                return problemId;
            }

            public String getTitle() {
                return "문제" + problemId;
            }

            public String getCategory() {
                return category;
            }

            public Difficulty getDifficulty() {
                return difficulty;
            }

            public Language getLanguage() {
                return language;
            }

            public Verdict getVerdict() {
                return verdict;
            }
        };
    }

    private TagRow tag(Long problemId, String tag) {
        return new TagRow() {
            public Long getProblemId() {
                return problemId;
            }

            public String getTag() {
                return tag;
            }
        };
    }
}

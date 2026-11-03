package com.example.cortebe.domain.user.service;

import com.example.cortebe.domain.judge.repository.JudgeJobRepository;
import com.example.cortebe.domain.judge.repository.JudgeJobRepository.SolvedSubmission;
import com.example.cortebe.domain.judge.runner.Verdict;
import com.example.cortebe.domain.user.dto.GrassResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GrassServiceTests {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    @Mock
    private JudgeJobRepository judgeJobRepository;

    @InjectMocks
    private GrassService grassService;

    @Test
    void 날짜별로_맞힌_서로_다른_문제_수를_센다() {
        // given
        List<SolvedSubmission> submissions = List.of(
            submission(1L, koreanTime("2026-11-02T10:00")),
            submission(1L, koreanTime("2026-11-02T11:00")),  // 같은 날 같은 문제를 또 맞힘 → 1번만 센다
            submission(2L, koreanTime("2026-11-02T12:00")),
            submission(1L, koreanTime("2026-11-03T09:00"))   // 다음 날 다시 맞힘 → 그날도 센다
        );
        given(judgeJobRepository.findSolvedSubmissions(eq(1L), eq(Verdict.ACCEPTED), any()))
            .willReturn(submissions);

        // when
        List<GrassResponse> grass = grassService.getMyGrass(1L);

        // then
        assertThat(grass).containsExactly(
            new GrassResponse(LocalDate.of(2026, 11, 2), 2),
            new GrassResponse(LocalDate.of(2026, 11, 3), 1)
        );
    }

    @Test
    void 날짜는_한국_시간_자정을_기준으로_나눈다() {
        // given
        List<SolvedSubmission> submissions = List.of(
            submission(1L, koreanTime("2026-11-02T23:59")),
            submission(2L, koreanTime("2026-11-03T00:00"))
        );
        given(judgeJobRepository.findSolvedSubmissions(eq(1L), eq(Verdict.ACCEPTED), any()))
            .willReturn(submissions);

        // when
        List<GrassResponse> grass = grassService.getMyGrass(1L);

        // then
        assertThat(grass).containsExactly(
            new GrassResponse(LocalDate.of(2026, 11, 2), 1),
            new GrassResponse(LocalDate.of(2026, 11, 3), 1)
        );
    }

    @Test
    void 오늘_포함_최근_365일의_첫날_0시부터_조회한다() {
        // given
        LocalDate firstDay = LocalDate.now(KOREA).minusDays(364);
        LocalDateTime from = koreanTime(firstDay + "T00:00");
        given(judgeJobRepository.findSolvedSubmissions(1L, Verdict.ACCEPTED, from))
            .willReturn(List.of(submission(1L, from)));

        // when
        List<GrassResponse> grass = grassService.getMyGrass(1L);

        // then
        assertThat(grass).containsExactly(new GrassResponse(firstDay, 1));
    }

    @Test
    void 푼_문제가_없으면_빈_목록을_준다() {
        // given
        given(judgeJobRepository.findSolvedSubmissions(eq(1L), eq(Verdict.ACCEPTED), any()))
            .willReturn(List.of());

        // when
        List<GrassResponse> grass = grassService.getMyGrass(1L);

        // then
        assertThat(grass).isEmpty();
    }

    // 한국 시간 문자열을 서버 시간대의 시각으로 바꾼다 (DB의 createdAt은 서버 시간대로 저장된다)
    private LocalDateTime koreanTime(String text) {
        return LocalDateTime.parse(text)
            .atZone(KOREA)
            .withZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime();
    }

    private SolvedSubmission submission(Long problemId, LocalDateTime createdAt) {
        return new SolvedSubmission() {
            @Override
            public Long getProblemId() {
                return problemId;
            }

            @Override
            public LocalDateTime getCreatedAt() {
                return createdAt;
            }
        };
    }
}

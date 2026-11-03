package com.example.cortebe.domain.user.service;

import com.example.cortebe.domain.judge.repository.JudgeJobRepository;
import com.example.cortebe.domain.judge.repository.JudgeJobRepository.SolvedSubmission;
import com.example.cortebe.domain.judge.runner.Verdict;
import com.example.cortebe.domain.user.dto.GrassResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GrassService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final JudgeJobRepository judgeJobRepository;

    public List<GrassResponse> getMyGrass(Long userId) {
        // 1. 조회 시작 시각: 오늘(한국 시간) 포함 최근 365일의 첫날 0시
        LocalDate firstDay = LocalDate.now(KOREA).minusDays(364);
        LocalDateTime from = koreanToServerTime(firstDay.atStartOfDay());

        // 2. 그 뒤로 내가 맞힌 제출을 모두 가져온다
        List<SolvedSubmission> submissions =
            judgeJobRepository.findSolvedSubmissions(userId, Verdict.ACCEPTED, from);

        // 3. 날짜마다 맞힌 문제 ID를 모은다
        //    Set은 같은 값을 한 번만 담아서 같은 날 같은 문제는 1개로 센다
        //    TreeMap은 날짜(key)를 오름차순으로 정렬해 둔다
        Map<LocalDate, Set<Long>> problemIdsByDate = new TreeMap<>();
        for (SolvedSubmission submission : submissions) {
            LocalDate date = serverTimeToKoreanDate(submission.getCreatedAt());

            if (!problemIdsByDate.containsKey(date)) {
                problemIdsByDate.put(date, new HashSet<>());
            }
            problemIdsByDate.get(date).add(submission.getProblemId());
        }

        // 4. 날짜와 문제 수로 응답을 만든다
        List<GrassResponse> grass = new ArrayList<>();
        for (LocalDate date : problemIdsByDate.keySet()) {
            int count = problemIdsByDate.get(date).size();
            grass.add(new GrassResponse(date, count));
        }
        return grass;
    }

    // DB의 시각은 서버 시간대(보통 UTC)로 저장돼서 한국 시간과 서로 바꿔야 한다
    private LocalDateTime koreanToServerTime(LocalDateTime koreanTime) {
        return koreanTime.atZone(KOREA)
            .withZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime();
    }

    private LocalDate serverTimeToKoreanDate(LocalDateTime serverTime) {
        return serverTime.atZone(ZoneId.systemDefault())
            .withZoneSameInstant(KOREA)
            .toLocalDate();
    }
}

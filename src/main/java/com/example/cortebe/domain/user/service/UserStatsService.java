package com.example.cortebe.domain.user.service;

import com.example.cortebe.domain.judge.repository.JudgeJobRepository;
import com.example.cortebe.domain.judge.repository.JudgeJobRepository.StatRow;
import com.example.cortebe.domain.judge.runner.Verdict;
import com.example.cortebe.domain.problem.entity.Difficulty;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.domain.problem.repository.ProblemRepository.TagRow;
import com.example.cortebe.domain.user.dto.UserStatsResponse;
import com.example.cortebe.domain.user.dto.UserStatsResponse.DifficultyCount;
import com.example.cortebe.domain.user.dto.UserStatsResponse.Group;
import com.example.cortebe.domain.user.dto.UserStatsResponse.HardestProblem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserStatsService {

    // 채점이 끝난 판정만 센다. 대기·실행 중·시스템 오류·취소는 사용자 실력과 관계없어서 뺀다
    private static final Set<Verdict> JUDGED_VERDICTS = EnumSet.of(Verdict.ACCEPTED, Verdict.WRONG_ANSWER,
        Verdict.COMPILE_ERROR, Verdict.RUNTIME_ERROR, Verdict.TIME_LIMIT, Verdict.MEMORY_LIMIT, Verdict.OUTPUT_LIMIT);

    private final JudgeJobRepository judgeJobRepository;
    private final ProblemRepository problemRepository;

    public UserStatsResponse getMyStats(Long userId) {
        List<StatRow> rows = judgeJobRepository.findStatRows(userId, JUDGED_VERDICTS);
        Map<Long, List<String>> tagsByProblemId = findTags(rows);

        Map<Verdict, Long> verdicts = new EnumMap<>(Verdict.class);
        JUDGED_VERDICTS.forEach(verdict -> verdicts.put(verdict, 0L));
        Map<Long, StatRow> solvedProblems = new HashMap<>();
        Counter total = new Counter();
        Map<String, Counter> languages = new HashMap<>();
        Map<String, Counter> tags = new HashMap<>();
        Map<String, Counter> categories = new HashMap<>();

        for (StatRow row : rows) {
            verdicts.merge(row.getVerdict(), 1L, Long::sum);
            if (row.getVerdict() == Verdict.ACCEPTED) {
                solvedProblems.putIfAbsent(row.getProblemId(), row);
            }
            total.add(row);
            languages.computeIfAbsent(row.getLanguage().name(), name -> new Counter()).add(row);
            categories.computeIfAbsent(row.getCategory(), name -> new Counter()).add(row);
            for (String tag : tagsByProblemId.getOrDefault(row.getProblemId(), List.of())) {
                tags.computeIfAbsent(tag, name -> new Counter()).add(row);
            }
        }

        return UserStatsResponse.builder()
            .solvedCount(total.solvedProblemIds.size())
            .submissionCount(total.submissions)
            .acceptedCount(total.accepted)
            .accuracy(accuracy(total.accepted, total.submissions))
            .verdicts(verdicts)
            .languages(toGroups(languages))
            .difficulties(countByDifficulty(solvedProblems.values()))
            .tags(toGroups(tags))
            .categories(toGroups(categories))
            .hardestProblem(findHardest(solvedProblems.values()))
            .build();
    }

    private Map<Long, List<String>> findTags(List<StatRow> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        Set<Long> problemIds = new HashSet<>();
        rows.forEach(row -> problemIds.add(row.getProblemId()));
        Map<Long, List<String>> tagsByProblemId = new HashMap<>();
        for (TagRow tag : problemRepository.findTagsByProblemIds(problemIds)) {
            tagsByProblemId.computeIfAbsent(tag.getProblemId(), id -> new ArrayList<>()).add(tag.getTag());
        }
        return tagsByProblemId;
    }

    private List<Group> toGroups(Map<String, Counter> counters) {
        return counters.entrySet().stream()
            .map(entry -> {
                Counter counter = entry.getValue();
                return new Group(entry.getKey(), counter.solvedProblemIds.size(), counter.submissions,
                    counter.accepted, accuracy(counter.accepted, counter.submissions));
            })
            .sorted(Comparator.comparingLong(Group::solvedCount).reversed()
                .thenComparing(Comparator.comparingLong(Group::submissionCount).reversed())
                .thenComparing(Group::name))
            .toList();
    }

    private List<DifficultyCount> countByDifficulty(Collection<StatRow> solvedProblems) {
        Map<Difficulty, Long> counts = new LinkedHashMap<>();
        for (Difficulty difficulty : Difficulty.values()) {
            counts.put(difficulty, 0L);
        }
        solvedProblems.forEach(problem -> counts.merge(problem.getDifficulty(), 1L, Long::sum));
        return counts.entrySet().stream()
            .map(entry -> new DifficultyCount(entry.getKey(), entry.getValue()))
            .toList();
    }

    private HardestProblem findHardest(Collection<StatRow> solvedProblems) {
        return solvedProblems.stream()
            .filter(problem -> problem.getDifficulty() != Difficulty.UNRATED)
            .max(Comparator.comparing(StatRow::getDifficulty)
                .thenComparing(StatRow::getProblemId, Comparator.reverseOrder()))
            .map(problem -> new HardestProblem(problem.getProblemId(), problem.getTitle(), problem.getDifficulty()))
            .orElse(null);
    }

    private double accuracy(long accepted, long submissions) {
        if (submissions == 0) {
            return 0.0;
        }
        return Math.round(accepted * 1000.0 / submissions) / 10.0;
    }

    // 언어·태그·분류 하나의 푼 문제, 제출 수, 맞힌 제출 수를 센다
    private static class Counter {
        private final Set<Long> solvedProblemIds = new HashSet<>();
        private long submissions;
        private long accepted;

        private void add(StatRow row) {
            submissions++;
            if (row.getVerdict() == Verdict.ACCEPTED) {
                accepted++;
                solvedProblemIds.add(row.getProblemId());
            }
        }
    }
}

package com.example.cortebe.domain.user.service;

import com.example.cortebe.domain.judge.runner.Verdict;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.domain.user.dto.ProblemStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProblemStatsService {

    private static final Set<Verdict> ATTEMPT_VERDICTS = Set.of(
        Verdict.ACCEPTED, Verdict.WRONG_ANSWER, Verdict.COMPILE_ERROR, Verdict.RUNTIME_ERROR,
        Verdict.TIME_LIMIT, Verdict.MEMORY_LIMIT, Verdict.OUTPUT_LIMIT
    );

    private final ProblemRepository problemRepository;

    public List<ProblemStatsResponse> getMyProblemStats(Long userId) {
        return problemRepository.findProblemStats(userId, ATTEMPT_VERDICTS, Verdict.ACCEPTED).stream()
            .map(ProblemStatsResponse::from)
            .sorted(Comparator.comparing(ProblemStatsResponse::category)
                .thenComparing(ProblemStatsResponse::difficulty))
            .toList();
    }
}

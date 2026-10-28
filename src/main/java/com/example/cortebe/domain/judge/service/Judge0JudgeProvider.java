package com.example.cortebe.domain.judge.service;

import com.example.cortebe.domain.judge.Judge0Properties;
import com.example.cortebe.domain.judge.JudgeStatus;
import com.example.cortebe.domain.judge.client.Judge0Client;
import com.example.cortebe.domain.judge.client.Judge0Language;
import com.example.cortebe.domain.judge.client.Judge0Status;
import com.example.cortebe.domain.judge.client.dto.Judge0Dto.BatchRequest;
import com.example.cortebe.domain.judge.client.dto.Judge0Dto.BatchResponse;
import com.example.cortebe.domain.judge.client.dto.Judge0Dto.SubmissionRequest;
import com.example.cortebe.domain.judge.client.dto.Judge0Dto.SubmissionResponse;
import com.example.cortebe.domain.judge.client.dto.Judge0Dto.TokenResponse;
import com.example.cortebe.domain.judge.dto.JudgeCase;
import com.example.cortebe.domain.judge.dto.JudgeRequest;
import com.example.cortebe.domain.judge.dto.JudgeResult;
import com.example.cortebe.domain.judge.dto.JudgeResult.CaseResult;
import com.example.cortebe.domain.judge.exception.JudgeFetchFailedException;
import com.example.cortebe.domain.judge.exception.JudgeSubmitFailedException;
import com.example.cortebe.domain.judge.exception.JudgeTimeoutException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class Judge0JudgeProvider implements JudgeProvider {

    // Judge0 batch 요청 한 번에 보낼 수 있는 최대 개수(MAX_SUBMISSION_BATCH_SIZE 기본값)
    private static final int BATCH_SIZE = 20;

    private final Judge0Client judge0Client;
    private final Judge0Properties judge0Properties;

    @Override
    public JudgeResult judge(JudgeRequest request) {
        // 빈 목록을 그대로 채점하면 ACCEPTED가 되므로 막는다
        if (request.cases().isEmpty()) {
            throw new IllegalArgumentException("채점할 테스트케이스가 없습니다.");
        }
        List<String> tokens = submit(request);
        Map<String, SubmissionResponse> finished = poll(tokens);
        return aggregate(tokens.stream().map(finished::get).map(this::toCaseResult).toList());
    }

    private List<String> submit(JudgeRequest request) {
        Judge0Language language = Judge0Language.from(request.language());
        List<SubmissionRequest> submissions = request.cases().stream()
            .map(judgeCase -> toSubmission(request, language, judgeCase))
            .toList();

        List<String> tokens = new ArrayList<>();
        for (List<SubmissionRequest> chunk : chunk(submissions)) {
            List<TokenResponse> response;
            try {
                response = judge0Client.createSubmissions(new BatchRequest(chunk));
            } catch (FeignException e) {
                log.warn("Judge0 제출 요청이 실패했습니다: {}", e.getMessage());
                throw new JudgeSubmitFailedException();
            }
            if (response == null || response.size() != chunk.size()
                || response.stream().anyMatch(token -> token == null || token.token() == null)) {
                log.warn("Judge0 제출 응답이 올바르지 않습니다: {}", response);
                throw new JudgeSubmitFailedException();
            }
            response.forEach(token -> tokens.add(token.token()));
        }
        return tokens;
    }

    private SubmissionRequest toSubmission(JudgeRequest request, Judge0Language language, JudgeCase judgeCase) {
        return new SubmissionRequest(
            encode(request.code()),
            language.getId(),
            language.getCompilerOptions(),
            encode(judgeCase.input()),
            encode(judgeCase.expectedOutput()),
            request.timeLimitMs() / 1000.0,
            request.memoryLimitMb() * 1024L
        );
    }

    // 끝난 테스트케이스가 idleTimeout 동안 하나도 늘지 않으면 Judge0가 멈춘 것으로 본다
    private Map<String, SubmissionResponse> poll(List<String> tokens) {
        Map<String, SubmissionResponse> finished = new HashMap<>();
        long lastProgress = System.nanoTime();

        while (finished.size() < tokens.size()) {
            sleep(judge0Properties.pollInterval());

            int before = finished.size();
            List<String> pending = tokens.stream().filter(token -> !finished.containsKey(token)).toList();
            for (List<String> chunk : chunk(pending)) {
                fetch(chunk).stream()
                    .filter(Objects::nonNull)
                    .filter(submission -> Judge0Status.from(submission.status().id()).isFinished())
                    .forEach(submission -> finished.put(submission.token(), submission));
            }

            if (finished.size() > before) {
                lastProgress = System.nanoTime();
            } else if (Duration.ofNanos(System.nanoTime() - lastProgress).compareTo(judge0Properties.idleTimeout()) > 0) {
                throw new JudgeTimeoutException();
            }
        }
        return finished;
    }

    private List<SubmissionResponse> fetch(List<String> tokens) {
        BatchResponse response;
        try {
            response = judge0Client.getSubmissions(String.join(",", tokens));
        } catch (FeignException e) {
            log.warn("Judge0 결과 조회가 실패했습니다: {}", e.getMessage());
            throw new JudgeFetchFailedException();
        }
        if (response == null || response.submissions() == null) {
            throw new JudgeFetchFailedException();
        }
        return response.submissions();
    }

    private CaseResult toCaseResult(SubmissionResponse submission) {
        Integer timeMs = submission.time() == null ? null
            : new BigDecimal(submission.time()).movePointRight(3).setScale(0, RoundingMode.HALF_UP).intValue();
        return new CaseResult(Judge0Status.from(submission.status().id()).getJudgeStatus(), timeMs, submission.memory());
    }

    private JudgeResult aggregate(List<CaseResult> cases) {
        JudgeStatus status = cases.stream()
            .map(CaseResult::status)
            .filter(judgeStatus -> judgeStatus != JudgeStatus.ACCEPTED)
            .findFirst()
            .orElse(JudgeStatus.ACCEPTED);

        Integer timeMs = cases.stream()
            .map(CaseResult::timeMs)
            .filter(Objects::nonNull)
            .max(Integer::compare)
            .orElse(null);

        Long memoryKb = cases.stream()
            .map(CaseResult::memoryKb)
            .filter(Objects::nonNull)
            .max(Long::compare)
            .orElse(null);

        return JudgeResult.builder()
            .status(status)
            .timeMs(timeMs)
            .memoryKb(memoryKb)
            .cases(cases)
            .build();
    }

    private String encode(String value) {
        if (value == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JudgeTimeoutException();
        }
    }

    private static <T> List<List<T>> chunk(List<T> list) {
        return IntStream.range(0, (list.size() + BATCH_SIZE - 1) / BATCH_SIZE)
            .mapToObj(i -> list.subList(i * BATCH_SIZE, Math.min(list.size(), (i + 1) * BATCH_SIZE)))
            .toList();
    }
}

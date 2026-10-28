package com.example.cortebe.domain.judge.service;

import com.example.cortebe.domain.judge.dto.CreateJobRequest;
import com.example.cortebe.domain.judge.dto.JobResponse;
import com.example.cortebe.domain.judge.entity.JudgeJob;
import com.example.cortebe.domain.judge.exception.JudgeConflictException;
import com.example.cortebe.domain.judge.exception.JudgeForbiddenException;
import com.example.cortebe.domain.judge.exception.JudgeNotFoundException;
import com.example.cortebe.domain.judge.repository.JudgeJobRepository;
import com.example.cortebe.domain.judge.repository.TestCaseRepository;
import com.example.cortebe.domain.judge.repository.JudgeCaseSnapshotRepository;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.judge.runner.Verdict;
import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.error.exception.InvalidInputException;
import com.example.cortebe.global.security.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobStore {
    private final JudgeJobRepository jobs;
    private final TestCaseRepository testCases;
    private final JudgeCaseSnapshotRepository snapshots;
    private final ProblemRepository problems;
    private final UserRepository users;
    private final ObjectMapper mapper;

    public record Case(String input, String expectedOutput) { }
    public record Snapshot(int timeLimitMs, int memoryLimitMb) { }
    public record Work(Long id, Language language, String code, boolean submission, int totalCount, Snapshot snapshot) { }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JobResponse create(Long userId, CreateJobRequest request, boolean submission) {
        if (request.code().getBytes(StandardCharsets.UTF_8).length > 102400) throw new InvalidInputException();
        User user = userId == null ? null : users.findById(userId).orElseThrow(UnauthorizedException::new);
        Problem problem = problems.findLockedById(request.problemId()).orElseThrow(JudgeNotFoundException::new);
        if (problem.getMemoryLimitMb() > 1024 || problem.getTimeLimitMs() > 60000) throw new JudgeConflictException();
        int count = submission ? Math.toIntExact(testCases.countByProblemId(problem.getId())) : 0;
        if (submission && count == 0) throw new JudgeConflictException();
        String token = token();
        JudgeJob job = jobs.saveAndFlush(JudgeJob.builder().problem(problem).author(user).submission(submission)
            .language(request.language()).code(request.code()).tokenHash(hash(token)).totalCount(count)
            .snapshot(mapper.writeValueAsString(new Snapshot(problem.getTimeLimitMs(), problem.getMemoryLimitMb()))).build());
        if (submission && snapshots.copy(job.getId(), problem.getId()) != count) throw new JudgeConflictException();
        return JobResponse.builder().id(job.getId()).verdict(job.getVerdict()).totalCount(job.getTotalCount())
            .accessToken(userId == null || !submission ? token : null)
            .streamPath(submission ? null : "/api/executions/" + job.getId() + "/stream").build();
    }

    public JobResponse get(Long id, Long userId, String token, boolean submission) {
        JudgeJob job = find(id);
        if (job.isSubmission() != submission) throw new JudgeNotFoundException();
        if (job.getAuthor() != null) {
            if (!job.isWrittenBy(userId)) throw new JudgeForbiddenException();
        } else if (!tokenMatches(job, token)) throw new JudgeForbiddenException();
        return JobResponse.from(job);
    }

    public void authorizeStream(Long id, String token) {
        JudgeJob job = find(id);
        if (job.isSubmission() || !tokenMatches(job, token)) throw new JudgeForbiddenException();
    }

    @Transactional
    public Work claim(Long id) {
        if (jobs.transition(id, Verdict.QUEUED, Verdict.RUNNING, LocalDateTime.now()) != 1) return null;
        JudgeJob job = find(id);
        return new Work(id, job.getLanguage(), job.getCode(), job.isSubmission(), job.getTotalCount(),
            mapper.readValue(job.getSnapshot(), Snapshot.class));
    }

    public List<Case> cases(Long id, int batch) {
        return snapshots.findBatch(id, PageRequest.of(batch, 8)).stream()
            .map(value -> new Case(value.getInput(), value.getExpectedOutput())).toList();
    }

    @Transactional
    public void finish(Long id, Verdict verdict, int passed, long runtime, String message) {
        jobs.finish(id, Verdict.RUNNING, verdict, passed, runtime, message, LocalDateTime.now());
        snapshots.deleteAllByJobId(id);
    }

    @Transactional
    public void reject(Long id) {
        jobs.finish(id, Verdict.QUEUED, Verdict.SYSTEM_ERROR, 0, 0, "실행 요청을 처리하지 못했습니다.", LocalDateTime.now());
        snapshots.deleteAllByJobId(id);
    }

    @Transactional
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now();
        snapshots.deleteExpired(now);
        jobs.deleteExpired(now);
    }

    @Transactional
    public void recover() {
        jobs.failInterrupted(Verdict.QUEUED, Verdict.RUNNING, Verdict.SYSTEM_ERROR, "서버 재시작으로 실행이 중단되었습니다.");
        snapshots.deleteFinished(Verdict.QUEUED, Verdict.RUNNING);
    }

    private JudgeJob find(Long id) {
        JudgeJob job = jobs.findWithAuthorById(id).orElseThrow(JudgeNotFoundException::new);
        if (job.getExpiresAt() != null && job.getExpiresAt().isBefore(LocalDateTime.now())) throw new JudgeNotFoundException();
        return job;
    }

    private boolean tokenMatches(JudgeJob job, String token) {
        return token != null && token.length() <= 128 && MessageDigest.isEqual(
            job.getTokenHash().getBytes(StandardCharsets.US_ASCII), hash(token).getBytes(StandardCharsets.US_ASCII));
    }

    private static String token() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("토큰 해시를 생성할 수 없습니다.", exception); }
    }
}

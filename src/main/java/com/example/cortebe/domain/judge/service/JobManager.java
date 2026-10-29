package com.example.cortebe.domain.judge.service;

import com.example.cortebe.domain.judge.dto.CreateJobRequest;
import com.example.cortebe.domain.judge.dto.JobResponse;
import com.example.cortebe.domain.judge.exception.JudgeLimitException;
import com.example.cortebe.domain.judge.exception.JudgeUnavailableException;
import com.example.cortebe.domain.judge.JudgeLanguage;
import com.example.cortebe.domain.judge.JudgeStatus;
import com.example.cortebe.domain.judge.dto.JudgeCase;
import com.example.cortebe.domain.judge.dto.JudgeRequest;
import com.example.cortebe.domain.judge.dto.JudgeResult;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.judge.runner.JudgeProperties;
import com.example.cortebe.domain.judge.runner.Verdict;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class JobManager {
    private final JobStore store;
    private final JudgeProvider provider;
    private final JudgeProperties properties;
    private final ThreadPoolExecutor executor;
    private final Semaphore capacity;
    private final AtomicBoolean accepting = new AtomicBoolean();
    private final Map<String, Rate> rates = new ConcurrentHashMap<>();
    private final Map<String, Integer> active = new HashMap<>();

    public JobManager(JobStore store, JudgeProvider provider, JudgeProperties properties) {
        this.store = store;
        this.provider = provider;
        this.properties = properties;
        executor = new ThreadPoolExecutor(properties.concurrency(), properties.concurrency(), 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(properties.queueCapacity()), Thread.ofPlatform().name("judge-", 0).factory());
        capacity = new Semaphore(properties.concurrency() + properties.queueCapacity());
    }

    private record Rate(long minute, int count) { }

    public JobResponse create(Long userId, String ip, CreateJobRequest request, boolean submission) {
        if (!properties.enabled() || !accepting.get()) throw new JudgeUnavailableException();
        limit("ip:" + ip);
        if (userId != null) limit("user:" + userId);
        if (!capacity.tryAcquire()) throw new JudgeLimitException();
        List<String> identities = userId == null ? List.of("ip:" + ip) : List.of("ip:" + ip, "user:" + userId);
        boolean acquired = false;
        PendingJob job = null;
        try {
            acquire(identities);
            acquired = true;
            JobResponse response = store.create(userId, request, submission);
            job = new PendingJob(response.id(), identities);
            executor.execute(job);
            return response;
        } catch (RuntimeException exception) {
            if (job != null) job.reject();
            else {
                if (acquired) release(identities);
                capacity.release();
            }
            if (exception instanceof RejectedExecutionException) throw new JudgeLimitException();
            throw exception;
        }
    }

    @PostConstruct
    public void recover() {
        // 웹 서버가 요청을 받기 전에 복구를 마치며 직접 생성한 객체도 복구 전에는 닫혀 있다.
        store.recover();
        accepting.set(true);
    }

    @Scheduled(fixedDelay = 60000)
    public void cleanup() {
        store.cleanup();
        long minute = Instant.now().getEpochSecond() / 60;
        rates.entrySet().removeIf(entry -> entry.getValue().minute() < minute - 1);
    }

    @PreDestroy
    public void close() {
        accepting.set(false);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) cancelWaiting(executor.shutdownNow());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            cancelWaiting(executor.shutdownNow());
        }
    }

    private void cancelWaiting(List<Runnable> waiting) {
        waiting.forEach(task -> ((PendingJob) task).cancel());
    }

    private synchronized void limit(String key) {
        long minute = Instant.now().getEpochSecond() / 60;
        if (!rates.containsKey(key) && rates.size() >= 10000) throw new JudgeLimitException();
        Rate old = rates.get(key);
        int count = old == null || old.minute() != minute ? 0 : old.count();
        if (count >= properties.requestsPerMinute()) throw new JudgeLimitException();
        rates.put(key, new Rate(minute, count + 1));
    }

    private synchronized void acquire(List<String> keys) {
        if (keys.stream().anyMatch(key -> active.getOrDefault(key, 0) >= properties.maxConcurrentPerIdentity())) {
            throw new JudgeLimitException();
        }
        keys.forEach(key -> active.merge(key, 1, Integer::sum));
    }

    private synchronized void release(List<String> keys) {
        keys.forEach(key -> active.computeIfPresent(key, (ignored, count) -> count <= 1 ? null : count - 1));
    }

    private JudgeResult judge(JobStore.Work work, List<JudgeCase> cases) {
        return provider.judge(JudgeRequest.builder().language(language(work.language())).code(work.code()).cases(cases)
            .timeLimitMs(work.snapshot().timeLimitMs()).memoryLimitMb(work.snapshot().memoryLimitMb()).build());
    }

    private void execute(Long id) {
        JobStore.Work work = store.claim(id);
        if (work == null) return;
        if (!work.submission()) {
            JudgeResult result = judge(work, List.of(new JudgeCase(work.stdin(), null)));
            JudgeResult.CaseResult single = result.cases().getFirst();
            Verdict verdict = result.status() == JudgeStatus.ACCEPTED ? Verdict.FINISHED : verdict(result.status());
            store.finishExecution(id, verdict, result.timeMs() == null ? 0 : result.timeMs(),
                single.compileOutput(), single.stdout(), single.stderr());
            return;
        }
        int passed = 0;
        long runtime = 0;
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
        for (int batch = 0; batch * 8 < work.totalCount(); batch++) {
            if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline) {
                store.finish(id, Verdict.SYSTEM_ERROR, passed, runtime, "채점 대기 시간이 초과되었습니다.");
                return;
            }
            List<JudgeCase> cases = store.cases(id, batch).stream()
                .map(value -> new JudgeCase(value.input(), value.expectedOutput())).toList();
            if (cases.isEmpty()) throw new IllegalStateException("채점 스냅샷이 없습니다.");
            JudgeResult result = judge(work, cases);
            for (JudgeResult.CaseResult single : result.cases()) {
                runtime += single.timeMs() == null ? 0 : single.timeMs();
                if (single.status() != JudgeStatus.ACCEPTED) {
                    store.finish(id, verdict(single.status()), passed, runtime, single.compileOutput());
                    return;
                }
                passed++;
            }
        }
        store.finish(id, Verdict.ACCEPTED, passed, runtime, null);
    }

    private static JudgeLanguage language(Language language) {
        return switch (language) {
            case C -> JudgeLanguage.C99;
            case CPP -> JudgeLanguage.CPP17;
            case JAVA -> JudgeLanguage.JAVA;
            case CSHARP -> JudgeLanguage.CSHARP;
            case JAVASCRIPT -> JudgeLanguage.JAVASCRIPT;
            case PYTHON -> JudgeLanguage.PYTHON;
        };
    }

    private static Verdict verdict(JudgeStatus status) {
        return switch (status) {
            case ACCEPTED -> Verdict.ACCEPTED;
            case WRONG_ANSWER -> Verdict.WRONG_ANSWER;
            case TIME_LIMIT_EXCEEDED -> Verdict.TIME_LIMIT;
            case RUNTIME_ERROR -> Verdict.RUNTIME_ERROR;
            case COMPILE_ERROR -> Verdict.COMPILE_ERROR;
            case OUTPUT_LIMIT -> Verdict.OUTPUT_LIMIT;
            case INTERNAL_ERROR -> Verdict.SYSTEM_ERROR;
        };
    }

    private final class PendingJob implements Runnable {
        final Long id;
        final List<String> identities;
        final AtomicBoolean released = new AtomicBoolean();

        PendingJob(Long id, List<String> identities) { this.id = id; this.identities = identities; }

        @Override
        public void run() {
            try { execute(id); }
            catch (Exception exception) {
                log.error("Judge0 실행·채점 작업 처리에 실패했습니다: {}", id, exception);
                store.finish(id, Verdict.SYSTEM_ERROR, 0, 0, "실행 환경 오류가 발생했습니다.");
            } finally { finish(); }
        }

        void cancel() {
            try { store.cancelQueued(id); } finally { finish(); }
        }

        void reject() {
            try { store.reject(id); } finally { finish(); }
        }

        void finish() {
            if (released.compareAndSet(false, true)) {
                release(identities);
                capacity.release();
            }
        }
    }
}

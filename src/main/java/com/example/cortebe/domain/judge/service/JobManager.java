package com.example.cortebe.domain.judge.service;

import com.example.cortebe.domain.judge.dto.CreateJobRequest;
import com.example.cortebe.domain.judge.dto.JobResponse;
import com.example.cortebe.domain.judge.exception.JudgeConflictException;
import com.example.cortebe.domain.judge.exception.JudgeLimitException;
import com.example.cortebe.domain.judge.exception.JudgeNotFoundException;
import com.example.cortebe.domain.judge.exception.JudgeUnavailableException;
import com.example.cortebe.domain.judge.runner.DockerRunner;
import com.example.cortebe.domain.judge.runner.JudgeProperties;
import com.example.cortebe.domain.judge.runner.Verdict;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Service
@Slf4j
public class JobManager {
    private static final int WAITING = 0;
    private static final int DISPATCHED = 1;
    private static final int RUNNING = 2;
    private static final int FINISHED = 3;
    private final JobStore store;
    private final DockerRunner runner;
    private final JudgeProperties properties;
    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService deadlines;
    private final Semaphore capacity;
    private final AtomicBoolean accepting = new AtomicBoolean();
    private final Map<Long, PendingJob> channels = new ConcurrentHashMap<>();
    private final Map<String, Rate> rates = new ConcurrentHashMap<>();
    private final Map<String, Integer> active = new HashMap<>();

    public JobManager(JobStore store, DockerRunner runner, JudgeProperties properties) {
        this.store = store;
        this.runner = runner;
        this.properties = properties;
        executor = new ThreadPoolExecutor(properties.concurrency(), properties.concurrency(), 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(properties.queueCapacity()), Thread.ofPlatform().name("judge-", 0).factory());
        deadlines = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("judge-connect-timeout").factory());
        capacity = new Semaphore(properties.concurrency() + properties.queueCapacity());
    }

    public record Event(String type, String data) { }
    private record Rate(long minute, int count) { }
    private record Outcome(Verdict verdict, int passed, long runtime, String message) { }

    public JobResponse create(Long userId, String ip, CreateJobRequest request, boolean submission) {
        if (!properties.enabled() || !runner.available() || !accepting.get()) throw new JudgeUnavailableException();
        limit("ip:" + ip);
        if (userId != null) limit("user:" + userId);
        if (channels.size() >= 1024 || !capacity.tryAcquire()) throw new JudgeLimitException();
        List<String> identities = userId == null ? List.of("ip:" + ip) : List.of("ip:" + ip, "user:" + userId);
        boolean acquired = false;
        PendingJob job = null;
        try {
            acquire(identities);
            acquired = true;
            JobResponse response = store.create(userId, request, submission);
            job = new PendingJob(response.id(), identities, submission);
            channels.put(job.id, job);
            if (submission) executor.execute(job);
            else job.timeout = deadlines.schedule(job::expire, properties.connectSeconds(), TimeUnit.SECONDS);
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

    public Channel connect(Long id, String token, Consumer<Event> consumer) {
        store.authorizeStream(id, token);
        PendingJob job = channels.get(id);
        if (job == null) throw new JudgeNotFoundException();
        if (!job.state.compareAndSet(WAITING, DISPATCHED)) throw new JudgeConflictException();
        try {
            job.channel.connect(consumer);
            if (job.timeout != null) job.timeout.cancel(false);
            executor.execute(job);
            return job.channel;
        } catch (RuntimeException exception) {
            job.reject();
            if (exception instanceof RejectedExecutionException) throw new JudgeLimitException();
            throw exception;
        }
    }

    @PostConstruct
    public void recover() {
        // 웹 서버가 요청을 받기 전에 복구를 마치며 직접 생성한 객체도 복구 전에는 닫혀 있다.
        runner.recoverContainers();
        store.recover();
        accepting.set(true);
    }

    @Scheduled(fixedDelay = 60000)
    public void cleanup() {
        store.cleanup();
        long now = System.currentTimeMillis();
        channels.entrySet().removeIf(entry -> entry.getValue().channel.finishedAt > 0
            && now - entry.getValue().channel.finishedAt > 60000);
        long minute = Instant.now().getEpochSecond() / 60;
        rates.entrySet().removeIf(entry -> entry.getValue().minute() < minute - 1);
    }

    @PreDestroy
    public void close() {
        accepting.set(false);
        channels.values().forEach(job -> job.channel.cancel());
        deadlines.shutdownNow();
        executor.shutdown();
        try { if (!executor.awaitTermination(30, TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); executor.shutdownNow(); }
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

    private Outcome execute(Long id, Channel channel) {
        JobStore.Work work = store.claim(id);
        if (work == null) return null;
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
        try (DockerRunner.PreparedProgram program = runner.prepare(work.language(), work.code(), channel.cancelled)) {
            if (program.compilation().verdict() != Verdict.FINISHED) {
                var result = program.compilation();
                return new Outcome(result.verdict(), 0, result.runtimeMs(), compileMessage(result));
            }
            if (!work.submission()) {
                var result = program.execute(work.snapshot().memoryLimitMb(), properties.sessionSeconds() * 1000L,
                    null, channel::ready, channel::output, channel.cancelled);
                return new Outcome(result.verdict(), 0, result.runtimeMs(), null);
            }
            int passed = 0;
            long runtime = 0;
            for (int batch = 0; batch * 8 < work.totalCount(); batch++) {
                List<JobStore.Case> cases = store.cases(id, batch);
                if (cases.isEmpty()) throw new IllegalStateException("채점 스냅샷이 없습니다.");
                for (JobStore.Case testCase : cases) {
                    if (System.nanoTime() >= deadline) return new Outcome(Verdict.TIME_LIMIT, passed, runtime, null);
                    var result = program.execute(work.snapshot().memoryLimitMb(), work.snapshot().timeLimitMs(),
                        testCase.input().getBytes(StandardCharsets.UTF_8), ignored -> { }, (type, bytes) -> { }, channel.cancelled);
                    runtime += result.runtimeMs();
                    if (result.verdict() != Verdict.FINISHED) return new Outcome(result.verdict(), passed, runtime, null);
                    if (!OutputComparator.matches(testCase.expectedOutput(), result.stdout())) {
                        return new Outcome(Verdict.WRONG_ANSWER, passed, runtime, null);
                    }
                    passed++;
                }
            }
            return new Outcome(Verdict.ACCEPTED, passed, runtime, null);
        }
    }

    private String compileMessage(DockerRunner.Result result) {
        return result.verdict() == Verdict.COMPILE_ERROR
            ? new String(result.stdout(), StandardCharsets.UTF_8) + result.stderr() : null;
    }

    private final class PendingJob implements Runnable {
        final Long id;
        final List<String> identities;
        final boolean submission;
        final Channel channel = new Channel(this::cancelQueued);
        final AtomicInteger state;
        final AtomicBoolean released = new AtomicBoolean();
        volatile ScheduledFuture<?> timeout;

        PendingJob(Long id, List<String> identities, boolean submission) {
            this.id = id;
            this.identities = identities;
            this.submission = submission;
            state = new AtomicInteger(submission ? DISPATCHED : WAITING);
        }

        @Override
        public void run() {
            if (!state.compareAndSet(DISPATCHED, RUNNING)) return;
            try {
                Outcome outcome = execute(id, channel);
                // 컨테이너 정리가 끝난 뒤 자리를 먼저 반환하고 최종 상태를 공개한다.
                finish();
                if (outcome != null) {
                    store.finish(id, outcome.verdict(), outcome.passed(), outcome.runtime(), outcome.message());
                    if (!submission) {
                        if (outcome.verdict() == Verdict.COMPILE_ERROR) channel.emit(new Event("compileError", outcome.message()));
                        channel.emit(new Event("exit", outcome.verdict().name()));
                    }
                }
            } catch (Exception exception) {
                log.error("채점 작업 처리에 실패했습니다: {}", id, exception);
                finish();
                store.finish(id, Verdict.SYSTEM_ERROR, 0, 0, "실행 환경 오류가 발생했습니다.");
                channel.emit(new Event("exit", Verdict.SYSTEM_ERROR.name()));
            } finally { finish(); }
        }

        void expire() {
            if (state.compareAndSet(WAITING, FINISHED)) {
                channel.cancelled.set(true);
                try { store.cancelQueued(id); } finally { finish(); }
            }
        }

        void cancelQueued() {
            if (state.compareAndSet(WAITING, FINISHED)
                || (state.get() == DISPATCHED && executor.remove(this) && state.compareAndSet(DISPATCHED, FINISHED))) {
                try { store.cancelQueued(id); } finally { finish(); }
                channel.emit(new Event("exit", Verdict.CANCELLED.name()));
            }
        }

        void reject() {
            state.set(FINISHED);
            try { store.reject(id); } finally { finish(); }
        }

        void finish() {
            state.set(FINISHED);
            channel.finish();
            if (timeout != null) timeout.cancel(false);
            if (released.compareAndSet(false, true)) {
                release(identities);
                capacity.release();
            }
        }
    }

    public static final class Channel {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final ArrayBlockingQueue<byte[]> inputs = new ArrayBlockingQueue<>(64);
        private final Runnable onCancel;
        private Consumer<Event> consumer;
        private volatile DockerRunner.Input input;
        private boolean eof;
        private int inputBytes;
        private volatile long finishedAt;

        private Channel(Runnable onCancel) { this.onCancel = onCancel; }

        private synchronized void connect(Consumer<Event> next) {
            if (consumer != null) throw new JudgeConflictException();
            consumer = next;
        }

        private synchronized void emit(Event event) {
            // 연결 전에 사용자 코드를 시작하지 않으므로 출력 이력을 보관할 필요가 없다.
            if (consumer != null) consumer.accept(event);
        }

        private void output(String type, byte[] bytes) {
            emit(new Event(type, Base64.getEncoder().encodeToString(bytes)));
        }

        private synchronized void ready(DockerRunner.Input value) {
            input = value;
            emit(new Event("ready", ""));
            Thread.startVirtualThread(() -> {
                try {
                    while (!cancelled.get() && finishedAt == 0) {
                        byte[] bytes = inputs.poll(100, TimeUnit.MILLISECONDS);
                        if (bytes != null) value.write(bytes);
                        synchronized (this) {
                            if (eof && inputs.isEmpty()) { value.eof(); return; }
                        }
                    }
                } catch (IOException exception) { cancel(); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); cancel(); }
            });
        }

        public synchronized void write(String text) {
            if (eof || finishedAt != 0) throw new JudgeConflictException();
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            inputBytes += bytes.length;
            if (inputBytes > 1048576 || !inputs.offer(bytes)) { cancel(); throw new JudgeLimitException(); }
        }

        public synchronized void eof() { eof = true; }

        public void cancel() {
            cancelled.set(true);
            DockerRunner.Input current = input;
            if (current != null) current.cancel();
            onCancel.run();
        }

        private void finish() { finishedAt = System.currentTimeMillis(); inputs.clear(); }
    }
}

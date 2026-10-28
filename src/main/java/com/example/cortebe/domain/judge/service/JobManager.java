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
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Service
@Slf4j
public class JobManager {
    private final JobStore store;
    private final DockerRunner runner;
    private final JudgeProperties properties;
    private final ThreadPoolExecutor executor;
    private final Semaphore capacity;
    private final Map<Long, Channel> channels = new ConcurrentHashMap<>();
    private final Map<String, Rate> rates = new ConcurrentHashMap<>();

    public JobManager(JobStore store, DockerRunner runner, JudgeProperties properties) {
        this.store = store;
        this.runner = runner;
        this.properties = properties;
        executor = new ThreadPoolExecutor(properties.concurrency(), properties.concurrency(), 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(properties.queueCapacity()), Thread.ofPlatform().name("judge-", 0).factory());
        capacity = new Semaphore(properties.concurrency() + properties.queueCapacity());
    }

    public record Event(String type, String data) { }
    private record Rate(long minute, int count) { }

    public JobResponse create(Long userId, String ip, CreateJobRequest request, boolean submission) {
        if (!properties.enabled() || !runner.available()) throw new JudgeUnavailableException();
        limit("ip:" + ip);
        if (userId != null) limit("user:" + userId);
        if (channels.size() >= 1024 || !capacity.tryAcquire()) throw new JudgeLimitException();
        Long id = null;
        try {
            JobResponse response = store.create(userId, request, submission);
            id = response.id();
            Channel channel = new Channel();
            channels.put(id, channel);
            executor.execute(() -> execute(response.id(), channel));
            return response;
        } catch (RuntimeException exception) {
            capacity.release();
            if (id != null) { channels.remove(id); store.reject(id); }
            if (exception instanceof RejectedExecutionException) throw new JudgeLimitException();
            throw exception;
        }
    }

    public Channel connect(Long id, String token, Consumer<Event> consumer) {
        store.authorizeStream(id, token);
        Channel channel = channels.get(id);
        if (channel == null) throw new JudgeNotFoundException();
        channel.connect(consumer);
        return channel;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        runner.recoverContainers();
        store.recover();
    }

    @Scheduled(fixedDelay = 60000)
    public void cleanup() {
        store.cleanup();
        long now = System.currentTimeMillis();
        channels.entrySet().removeIf(entry -> entry.getValue().finishedAt > 0 && now - entry.getValue().finishedAt > 60000);
        long minute = Instant.now().getEpochSecond() / 60;
        rates.entrySet().removeIf(entry -> entry.getValue().minute() < minute - 1);
    }

    @PreDestroy
    public void close() {
        channels.values().forEach(Channel::cancel);
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

    private void execute(Long id, Channel channel) {
        try {
            JobStore.Work work = store.claim(id);
            if (work == null) return;
            if (!work.submission()) {
                DockerRunner.Result result = runner.execute(work.language(), work.code(), work.snapshot().memoryLimitMb(),
                    properties.sessionSeconds() * 1000L, null, channel::ready, channel::output, channel.cancelled);
                store.finish(id, result.verdict(), 0, result.runtimeMs(), compileMessage(result));
                if (result.verdict() == Verdict.COMPILE_ERROR) channel.emit(new Event("compileError", compileMessage(result)));
                channel.emit(new Event("exit", result.verdict().name()));
                return;
            }
            int passed = 0;
            long runtime = 0;
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
            Verdict verdict = Verdict.ACCEPTED;
            String message = null;
            judging:
            for (int batch = 0; batch * 8 < work.totalCount(); batch++) {
                List<JobStore.Case> testCases = store.cases(id, batch);
                if (testCases.isEmpty()) throw new IllegalStateException("채점 스냅샷이 없습니다.");
                for (JobStore.Case testCase : testCases) {
                    if (System.nanoTime() >= deadline) { verdict = Verdict.TIME_LIMIT; break judging; }
                    DockerRunner.Result result = runner.execute(work.language(), work.code(), work.snapshot().memoryLimitMb(),
                        work.snapshot().timeLimitMs(), testCase.input().getBytes(StandardCharsets.UTF_8),
                        ignored -> { }, (type, bytes) -> { }, channel.cancelled);
                    runtime += result.runtimeMs();
                    if (result.verdict() != Verdict.FINISHED) {
                        verdict = result.verdict();
                        // 컴파일에는 테스트 입력을 전달하지 않아 비공개 입력이 섞이지 않는다.
                        message = compileMessage(result);
                        break judging;
                    }
                    if (!OutputComparator.matches(testCase.expectedOutput(), result.stdout())) {
                        verdict = Verdict.WRONG_ANSWER;
                        break judging;
                    }
                    passed++;
                }
            }
            store.finish(id, verdict, passed, runtime, message);
        } catch (Exception exception) {
            log.error("채점 작업 처리에 실패했습니다: {}", id, exception);
            store.finish(id, Verdict.SYSTEM_ERROR, 0, 0, "실행 환경 오류가 발생했습니다.");
            channel.emit(new Event("exit", Verdict.SYSTEM_ERROR.name()));
        } finally {
            channel.finish();
            capacity.release();
        }
    }

    private String compileMessage(DockerRunner.Result result) {
        if (result.verdict() != Verdict.COMPILE_ERROR) return null;
        return new String(result.stdout(), StandardCharsets.UTF_8) + result.stderr();
    }

    public static final class Channel {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final List<Event> history = new ArrayList<>();
        private final ArrayBlockingQueue<byte[]> inputs = new ArrayBlockingQueue<>(64);
        private Consumer<Event> consumer;
        private volatile DockerRunner.Input input;
        private boolean eof;
        private int inputBytes;
        private volatile long finishedAt;

        private synchronized void connect(Consumer<Event> next) {
            if (consumer != null) throw new JudgeConflictException();
            consumer = next;
            history.forEach(next);
            history.clear();
        }

        private synchronized void emit(Event event) {
            if (consumer == null) history.add(event);
            else consumer.accept(event);
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
        }

        private void finish() { finishedAt = System.currentTimeMillis(); inputs.clear(); }
    }
}

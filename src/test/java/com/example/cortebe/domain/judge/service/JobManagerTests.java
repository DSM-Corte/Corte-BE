package com.example.cortebe.domain.judge.service;

import com.example.cortebe.domain.judge.dto.CreateJobRequest;
import com.example.cortebe.domain.judge.dto.JobResponse;
import com.example.cortebe.domain.judge.exception.JudgeLimitException;
import com.example.cortebe.domain.judge.exception.JudgeUnavailableException;
import com.example.cortebe.domain.judge.runner.DockerRunner;
import com.example.cortebe.domain.judge.runner.JudgeProperties;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.judge.runner.Verdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class JobManagerTests {
    @Mock JobStore store;
    @Mock DockerRunner runner;
    @Mock DockerRunner.PreparedProgram prepared;

    @Test
    void 대기중인_직접실행도_취소하면_종료이벤트와_자리를_반환한다() throws Exception {
        // given
        given(runner.available()).willReturn(true);
        AtomicLong ids = new AtomicLong();
        given(store.create(isNull(), any(), anyBoolean())).willAnswer(invocation -> JobResponse.builder().id(ids.incrementAndGet()).build());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        given(store.claim(1L)).willAnswer(invocation -> { started.countDown(); release.await(5, TimeUnit.SECONDS); return null; });
        JobManager manager = new JobManager(store, runner, new JudgeProperties(true, "docker", 1, 1, 30, 120, 60, 1048576));
        manager.recover();
        CreateJobRequest request = new CreateJobRequest(1L, Language.PYTHON, "input()");
        try {
            manager.create(null, "first-ip", request, true);
            assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
            manager.create(null, "second-ip", request, false);
            java.util.List<JobManager.Event> events = new java.util.concurrent.CopyOnWriteArrayList<>();
            var channel = manager.connect(2L, "token", events::add);
            // when
            channel.cancel();
            // then
            verify(store).cancelQueued(2L);
            verify(store, never()).claim(2L);
            assertThat(events).contains(new JobManager.Event("exit", "CANCELLED"));
            manager.create(null, "second-ip", request, false);
        } finally { release.countDown(); manager.close(); }
    }

    @Test
    void 여러_케이스를_채점할_때_준비는_한_번만_수행한다() {
        // given
        given(runner.available()).willReturn(true);
        given(store.create(isNull(), any(), anyBoolean())).willReturn(JobResponse.builder().id(1L).build());
        given(store.claim(1L)).willReturn(new JobStore.Work(1L, Language.PYTHON, "print(3)", true, 2, new JobStore.Snapshot(1000, 256)));
        given(store.cases(1L, 0)).willReturn(java.util.List.of(new JobStore.Case("", "3\n"), new JobStore.Case("", "3\n")));
        given(runner.prepare(any(), any(), any())).willReturn(prepared);
        given(prepared.compilation()).willReturn(new DockerRunner.Result(Verdict.FINISHED, new byte[0], "", 0));
        given(prepared.execute(eq(256), eq(1000L), any(), any(), any(), any()))
            .willReturn(new DockerRunner.Result(Verdict.FINISHED, "3\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), "", 1));
        JobManager manager = new JobManager(store, runner, new JudgeProperties(true, "docker", 2, 16, 30, 120, 60, 1048576));
        manager.recover();
        try {
            // when
            manager.create(null, "ip", new CreateJobRequest(1L, Language.PYTHON, "print(3)"), true);
            // then
            verify(store, timeout(5000)).finish(1L, Verdict.ACCEPTED, 2, 2L, null);
            verify(runner, times(1)).prepare(any(), any(), any());
            verify(prepared, times(2)).execute(eq(256), eq(1000L), any(), any(), any(), any());
            verify(prepared, times(1)).close();
        } finally { manager.close(); }
    }

    @Test
    void 미연결_직접실행은_만료되고_동일_IP의_자리를_반환한다() throws Exception {
        // given
        given(runner.available()).willReturn(true);
        AtomicLong ids = new AtomicLong();
        given(store.create(isNull(), any(), anyBoolean())).willAnswer(invocation -> JobResponse.builder().id(ids.incrementAndGet()).build());
        var settings = new JudgeProperties(true, "docker", 2, 16, 30, 120, 60, 1048576, 1, 1, 10);
        JobManager manager = new JobManager(store, runner, settings);
        manager.recover();
        CreateJobRequest request = new CreateJobRequest(1L, Language.PYTHON, "input()");
        try {
            // when
            manager.create(null, "same-ip", request, false);
            // then
            assertThatThrownBy(() -> manager.create(null, "same-ip", request, false)).isInstanceOf(JudgeLimitException.class);
            verify(store, timeout(3000)).cancelQueued(1L);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (true) {
                try { manager.create(null, "same-ip", request, false); break; }
                catch (JudgeLimitException exception) {
                    if (System.nanoTime() >= deadline) throw exception;
                    Thread.sleep(10);
                }
            }
            verify(store, never()).claim(anyLong());
        } finally { manager.close(); }
    }

    @Test
    void 복구가_끝나기_전에는_작업을_접수하지_않는다() {
        // given
        given(runner.available()).willReturn(true);
        JobManager manager = new JobManager(store, runner, new JudgeProperties(true, "docker", 2, 16, 30, 120, 60, 1048576));
        try {
            // when & then
            assertThatThrownBy(() -> manager.create(null, "ip", new CreateJobRequest(1L, Language.PYTHON, "print(3)"), true))
                .isInstanceOf(JudgeUnavailableException.class);
        } finally { manager.close(); }
    }

    @Test
    void 직접_실행은_연결하기_전에는_작업자를_차지하지_않는다() throws Exception {
        // given
        given(runner.available()).willReturn(true);
        given(store.create(isNull(), any(), anyBoolean())).willReturn(JobResponse.builder().id(1L).build());
        CountDownLatch claimed = new CountDownLatch(1);
        given(store.claim(anyLong())).willAnswer(invocation -> { claimed.countDown(); return null; });
        JobManager manager = new JobManager(store, runner, new JudgeProperties(true, "docker", 2, 16, 30, 120, 60, 1048576));
        manager.recover();
        try {
            // when
            manager.create(null, "ip", new CreateJobRequest(1L, Language.PYTHON, "print(3)"), false);
            // then
            assertThat(claimed.await(1, TimeUnit.SECONDS)).isFalse();
            manager.connect(1L, "token", event -> { });
            assertThat(claimed.await(5, TimeUnit.SECONDS)).isTrue();
        } finally { manager.close(); }
    }

    @Test
    void 실행중인_작업과_유한_대기열이_가득차면_요청을_거절한다() throws Exception {
        // given
        given(runner.available()).willReturn(true);
        AtomicLong ids = new AtomicLong();
        given(store.create(isNull(), any(), anyBoolean())).willAnswer(invocation ->
            JobResponse.builder().id(ids.incrementAndGet()).verdict(Verdict.QUEUED).build());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        given(store.claim(anyLong())).willAnswer(invocation -> {
            started.countDown(); release.await(5, TimeUnit.SECONDS); return null;
        });
        JobManager manager = new JobManager(store, runner, new JudgeProperties(true, "docker", 1, 1, 30, 120, 60, 1048576));
        manager.recover();
        CreateJobRequest request = new CreateJobRequest(1L, Language.PYTHON, "print(3)");
        try {
            // when
            manager.create(null, "127.0.0.2", request, true);
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            manager.create(null, "127.0.0.1", request, true);
            // then
            assertThatThrownBy(() -> manager.create(null, "127.0.0.3", request, true)).isInstanceOf(JudgeLimitException.class);
        } finally { release.countDown(); manager.close(); }
    }

    @Test
    void 동일_IP의_분당_요청_수를_제한한다() {
        // given
        given(runner.available()).willReturn(true);
        given(store.create(isNull(), any(), anyBoolean())).willReturn(JobResponse.builder().id(1L).build());
        JobManager manager = new JobManager(store, runner, new JudgeProperties(true, "docker", 1, 1, 1, 120, 60, 1048576));
        manager.recover();
        CreateJobRequest request = new CreateJobRequest(1L, Language.PYTHON, "print(3)");
        try {
            // when
            manager.create(null, "127.0.0.1", request, true);
            // then
            assertThatThrownBy(() -> manager.create(null, "127.0.0.1", request, true)).isInstanceOf(JudgeLimitException.class);
        } finally { manager.close(); }
    }
}

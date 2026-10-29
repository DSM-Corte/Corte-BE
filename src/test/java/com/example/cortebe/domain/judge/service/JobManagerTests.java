package com.example.cortebe.domain.judge.service;

import com.example.cortebe.domain.judge.JudgeLanguage;
import com.example.cortebe.domain.judge.JudgeStatus;
import com.example.cortebe.domain.judge.dto.CreateJobRequest;
import com.example.cortebe.domain.judge.dto.JobResponse;
import com.example.cortebe.domain.judge.dto.JudgeCase;
import com.example.cortebe.domain.judge.dto.JudgeRequest;
import com.example.cortebe.domain.judge.dto.JudgeResult;
import com.example.cortebe.domain.judge.exception.JudgeLimitException;
import com.example.cortebe.domain.judge.exception.JudgeUnavailableException;
import com.example.cortebe.domain.judge.runner.JudgeProperties;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.judge.runner.Verdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class JobManagerTests {
    @Mock JobStore store;
    @Mock JudgeProvider provider;

    @Test
    void 직접실행은_미리_받은_입력과_언어별_제한을_전달하고_출력을_저장한다() {
        // given
        given(store.create(isNull(), any(), anyBoolean())).willReturn(JobResponse.builder().id(1L).build());
        given(store.claim(1L)).willReturn(new JobStore.Work(1L, Language.CSHARP, "code", false, 0,
            new JobStore.Snapshot(1500, 64), "1 2\n"));
        given(provider.judge(any())).willReturn(result(JudgeStatus.ACCEPTED, "3\n", "diagnostic"));
        JobManager manager = manager(2, 16, 30);
        manager.recover();
        try {
            // when
            manager.create(null, "ip", request(), false);
            // then
            verify(store, timeout(5000)).finishExecution(1L, Verdict.FINISHED, 12, "", "3\n", "diagnostic");
            ArgumentCaptor<JudgeRequest> sent = ArgumentCaptor.forClass(JudgeRequest.class);
            verify(provider).judge(sent.capture());
            assertThat(sent.getValue().language()).isEqualTo(JudgeLanguage.CSHARP);
            assertThat(sent.getValue().memoryLimitMb()).isEqualTo(64);
            assertThat(sent.getValue().timeLimitMs()).isEqualTo(1500);
            assertThat(sent.getValue().cases()).containsExactly(new JudgeCase("1 2\n", null));
        } finally { manager.close(); }
    }

    @Test
    void 제출은_스냅샷을_전달하고_첫_실패_이전의_통과수를_저장한다() {
        // given
        given(store.create(isNull(), any(), anyBoolean())).willReturn(JobResponse.builder().id(1L).build());
        given(store.claim(1L)).willReturn(new JobStore.Work(1L, Language.PYTHON, "code", true, 2,
            new JobStore.Snapshot(1000, 256), null));
        given(store.cases(1L, 0)).willReturn(List.of(new JobStore.Case("a", "a"), new JobStore.Case("b", "b")));
        given(provider.judge(any())).willReturn(JudgeResult.builder().status(JudgeStatus.WRONG_ANSWER).cases(List.of(
            new JudgeResult.CaseResult(JudgeStatus.ACCEPTED, 12, 100L, "a", "", ""),
            new JudgeResult.CaseResult(JudgeStatus.WRONG_ANSWER, 20, 100L, "bad", "", ""))).build());
        JobManager manager = manager(2, 16, 30);
        manager.recover();
        try {
            // when
            manager.create(null, "ip", request(), true);
            // then
            verify(store, timeout(5000)).finish(1L, Verdict.WRONG_ANSWER, 1, 32, "");
            verify(store, never()).finishExecution(any(), any(), org.mockito.ArgumentMatchers.anyLong(), any(), any(), any());
        } finally { manager.close(); }
    }

    @Test
    void 공급자_오류는_SYSTEM_ERROR로_저장하고_같은_IP의_자리를_돌려준다() throws Exception {
        // given
        AtomicLong ids = new AtomicLong();
        given(store.create(isNull(), any(), anyBoolean())).willAnswer(i -> JobResponse.builder().id(ids.incrementAndGet()).build());
        given(store.claim(any())).willAnswer(i -> new JobStore.Work(i.getArgument(0), Language.PYTHON, "code", false,
            0, new JobStore.Snapshot(1000, 256), ""));
        given(provider.judge(any())).willThrow(new IllegalStateException("test"));
        JobManager manager = manager(1, 1, 30);
        manager.recover();
        try {
            // when
            manager.create(null, "ip", request(), false);
            // then
            verify(store, timeout(5000)).finish(1L, Verdict.SYSTEM_ERROR, 0, 0, "실행 환경 오류가 발생했습니다.");
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (true) {
                try { manager.create(null, "ip", request(), false); break; }
                catch (JudgeLimitException exception) {
                    if (System.nanoTime() >= until) throw exception;
                    Thread.sleep(10);
                }
            }
            verify(store, timeout(5000)).finish(2L, Verdict.SYSTEM_ERROR, 0, 0, "실행 환경 오류가 발생했습니다.");
        } finally { manager.close(); }
    }

    @Test
    void 동시_요청은_IP_제한과_전체_큐_제한을_유지한다() throws Exception {
        // given
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicLong ids = new AtomicLong();
        given(store.create(isNull(), any(), anyBoolean())).willAnswer(i -> JobResponse.builder().id(ids.incrementAndGet()).build());
        given(store.claim(any())).willAnswer(i -> new JobStore.Work(i.getArgument(0), Language.PYTHON, "code", false,
            0, new JobStore.Snapshot(1000, 256), ""));
        given(provider.judge(any())).willAnswer(i -> { started.countDown(); finish.await(5, TimeUnit.SECONDS);
            return result(JudgeStatus.ACCEPTED, "", ""); });
        JobManager manager = manager(1, 1, 30);
        manager.recover();
        try {
            // when
            manager.create(null, "ip1", request(), false);
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            // then
            assertThatThrownBy(() -> manager.create(null, "ip1", request(), false)).isInstanceOf(JudgeLimitException.class);
            manager.create(null, "ip2", request(), false);
            assertThatThrownBy(() -> manager.create(null, "ip3", request(), false)).isInstanceOf(JudgeLimitException.class);
        } finally { finish.countDown(); manager.close(); }
    }

    @Test
    void 복구_전과_기능이_꺼진_상태에서는_요청을_거절한다() {
        // given
        JobManager manager = manager(2, 16, 30);
        // when & then
        try {
            assertThatThrownBy(() -> manager.create(null, "ip", request(), false)).isInstanceOf(JudgeUnavailableException.class);
        } finally { manager.close(); }
        JobManager disabled = new JobManager(store, provider, new JudgeProperties(false, 2, 16, 30, 1));
        disabled.recover();
        try {
            assertThatThrownBy(() -> disabled.create(null, "ip", request(), true)).isInstanceOf(JudgeUnavailableException.class);
        } finally { disabled.close(); }
    }

    @Test
    void 분당_요청_수를_초과하면_거절한다() {
        // given
        given(store.create(isNull(), any(), anyBoolean())).willReturn(JobResponse.builder().id(1L).build());
        JobManager manager = manager(2, 16, 1);
        manager.recover();
        try {
            // when
            manager.create(null, "ip", request(), false);
            // then
            assertThatThrownBy(() -> manager.create(null, "ip", request(), false)).isInstanceOf(JudgeLimitException.class);
        } finally { manager.close(); }
    }

    private JobManager manager(int workers, int queue, int rate) {
        return new JobManager(store, provider, new JudgeProperties(true, workers, queue, rate, 1));
    }

    private CreateJobRequest request() { return new CreateJobRequest(1L, Language.PYTHON, "code", "1 2\n"); }

    private JudgeResult result(JudgeStatus status, String stdout, String stderr) {
        return JudgeResult.builder().status(status).timeMs(12).memoryKb(100L)
            .cases(List.of(new JudgeResult.CaseResult(status, 12, 100L, stdout, stderr, ""))).build();
    }
}

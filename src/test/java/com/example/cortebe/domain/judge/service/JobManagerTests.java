package com.example.cortebe.domain.judge.service;

import com.example.cortebe.domain.judge.dto.CreateJobRequest;
import com.example.cortebe.domain.judge.dto.JobResponse;
import com.example.cortebe.domain.judge.exception.JudgeLimitException;
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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class JobManagerTests {
    @Mock JobStore store;
    @Mock DockerRunner runner;

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
        CreateJobRequest request = new CreateJobRequest(1L, Language.PYTHON, "print(3)");
        try {
            // when
            manager.create(null, "127.0.0.1", request, true);
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            manager.create(null, "127.0.0.1", request, true);
            // then
            assertThatThrownBy(() -> manager.create(null, "127.0.0.1", request, true)).isInstanceOf(JudgeLimitException.class);
        } finally { release.countDown(); manager.close(); }
    }

    @Test
    void 동일_IP의_분당_요청_수를_제한한다() {
        // given
        given(runner.available()).willReturn(true);
        given(store.create(isNull(), any(), anyBoolean())).willReturn(JobResponse.builder().id(1L).build());
        JobManager manager = new JobManager(store, runner, new JudgeProperties(true, "docker", 1, 1, 1, 120, 60, 1048576));
        CreateJobRequest request = new CreateJobRequest(1L, Language.PYTHON, "print(3)");
        try {
            // when
            manager.create(null, "127.0.0.1", request, true);
            // then
            assertThatThrownBy(() -> manager.create(null, "127.0.0.1", request, true)).isInstanceOf(JudgeLimitException.class);
        } finally { manager.close(); }
    }
}

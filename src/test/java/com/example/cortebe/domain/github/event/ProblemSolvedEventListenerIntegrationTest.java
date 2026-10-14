package com.example.cortebe.domain.github.event;

import com.example.cortebe.domain.github.service.GithubService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest
class ProblemSolvedEventListenerIntegrationTest {

    private final ProblemSolvedEvent event = ProblemSolvedEvent.builder().problemId(1000L).owner("owner").repo("repo").build();

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private GithubService githubService;

    @Test
    void 트랜잭션이_커밋되면_다른_스레드에서_저장한다() {
        // given
        AtomicReference<Thread> savingThread = new AtomicReference<>();
        willAnswer(invocation -> {
            savingThread.set(Thread.currentThread());
            return null;
        }).given(githubService).saveSolution(event);

        // when
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

        // then
        verify(githubService, timeout(2000)).saveSolution(event);
        assertThat(savingThread.get()).isNotEqualTo(Thread.currentThread());
    }

    @Test
    void 트랜잭션이_롤백되면_저장하지_않는다() {
        // when
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            status.setRollbackOnly();
        });

        // then
        verify(githubService, after(500).never()).saveSolution(any());
    }

    @Test
    void 트랜잭션_밖에서_발행하면_저장하지_않는다() {
        // when
        eventPublisher.publishEvent(event);

        // then
        verify(githubService, after(500).never()).saveSolution(any());
    }
}

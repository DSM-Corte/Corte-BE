package com.example.cortebe.domain.github.event;

import com.example.cortebe.domain.github.service.GithubService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class ProblemSolvedEventListenerTest {

    @Mock
    private GithubService githubService;

    @InjectMocks
    private ProblemSolvedEventListener listener;

    @Test
    void 저장에_실패해도_예외를_밖으로_던지지_않는다() {
        // given
        ProblemSolvedEvent event = ProblemSolvedEvent.builder().problemId(1000L).owner("owner").repo("repo").build();
        willThrow(new IllegalStateException("GitHub App private key가 설정되지 않았습니다."))
            .given(githubService).saveSolution(event);

        // when & then
        assertThatCode(() -> listener.handle(event)).doesNotThrowAnyException();
    }
}

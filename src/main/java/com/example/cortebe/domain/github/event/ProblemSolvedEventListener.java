package com.example.cortebe.domain.github.event;

import com.example.cortebe.domain.github.service.GithubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProblemSolvedEventListener {

    private final GithubService githubService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ProblemSolvedEvent event) {
        try {
            githubService.saveSolution(event);
        } catch (Exception e) {
            log.error("GitHub 풀이 저장 실패: problemId={}, repo={}/{}",
                event.problemId(), event.owner(), event.repo(), e);
        }
    }
}

package com.example.cortebe.domain.workbook.service;

import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.domain.problem.service.ProblemService;
import com.example.cortebe.domain.workbook.dto.AddWorkbookProblemRequest;
import com.example.cortebe.domain.workbook.dto.CreateWorkbookRequest;
import com.example.cortebe.domain.workbook.exception.DuplicateWorkbookProblemException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WorkbookConcurrencyTests {

    @Autowired private WorkbookService workbooks;
    @Autowired private ProblemRepository problems;
    @Autowired private ProblemService problemService;

    @Test
    void 동시에_같은_문제를_추가하면_하나만_성공한다() throws Exception {
        // given
        Long workbookId = workbooks.create(new CreateWorkbookRequest("동시성")).id();
        Long problemId = problems.save(Problem.builder().title("문제").description("설명").category("구현")
            .memoryLimitMb(256).timeLimitMs(1000).build()).getId();
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<String> request = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    workbooks.addProblem(workbookId, new AddWorkbookProblemRequest(problemId));
                    return "성공";
                } catch (DuplicateWorkbookProblemException exception) {
                    return "중복";
                }
            };
            // when
            var first = executor.submit(request);
            var second = executor.submit(request);
            // then
            assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder("성공", "중복");
            assertThat(workbooks.get(workbookId, 0, 20).problemCount()).isEqualTo(1);
        } finally {
            workbooks.delete(workbookId);
            problemService.delete(problemId);
        }
    }
}

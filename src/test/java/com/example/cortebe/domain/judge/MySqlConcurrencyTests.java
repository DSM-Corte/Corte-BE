package com.example.cortebe.domain.judge;

import com.example.cortebe.domain.judge.dto.CreateJobRequest;
import com.example.cortebe.domain.judge.dto.CreateTestCaseRequest;
import com.example.cortebe.domain.judge.exception.JudgeConflictException;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.judge.runner.Verdict;
import com.example.cortebe.domain.judge.service.JobStore;
import com.example.cortebe.domain.judge.service.TestCaseService;
import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "JUDGE_MYSQL_TESTS", matches = "true")
class MySqlConcurrencyTests {
    @Autowired ProblemRepository problems;
    @Autowired TestCaseService cases;
    @Autowired JobStore store;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("JUDGE_MYSQL_URL"));
        registry.add("spring.datasource.username", () -> "judge");
        registry.add("spring.datasource.password", () -> "judge-test-only");
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Test
    void 실제_MySQL에서도_동시_생성은_100개_한도를_지킨다() throws Exception {
        // given
        Long id = problem();
        for (int i = 0; i < 99; i++) cases.create(id, new CreateTestCaseRequest("", "", false, i));
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> create = () -> {
            start.await();
            try { cases.create(id, new CreateTestCaseRequest("", "", false, 99)); return true; }
            catch (JudgeConflictException exception) { return false; }
        };
        // when & then
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(create);
            var second = executor.submit(create);
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
            assertThat(cases.list(id, true, 0, 100).totalElements()).isEqualTo(100);
        }
    }

    @Test
    void 실제_MySQL에서_스냅샷을_복사하고_중복_실행과_갱신을_막는다() {
        // given
        Long id = problems.saveAndFlush(Problem.builder().title("합").description("두 수").category("구현")
            .timeLimitMs(1000).memoryLimitMb(256)
            .languageMemoryLimitsMb(java.util.Map.of(Language.PYTHON, 64)).build()).getId();
        Long caseId = cases.create(id, new CreateTestCaseRequest("1 2\n", "3\n", false, 0)).id();
        var job = store.create(null, new CreateJobRequest(id, Language.PYTHON, "print(3)"), true);
        cases.delete(id, caseId);
        // when
        var work = store.claim(job.id());
        // then
        assertThat(work.totalCount()).isEqualTo(1);
        assertThat(work.snapshot().memoryLimitMb()).isEqualTo(64);
        assertThat(store.cases(job.id(), 0).getFirst().expectedOutput()).isEqualTo("3\n");
        assertThat(store.claim(job.id())).isNull();
        store.finish(job.id(), Verdict.ACCEPTED, 1, 10, null);
        store.finish(job.id(), Verdict.SYSTEM_ERROR, 0, 0, "중복");
        assertThat(store.get(job.id(), null, job.accessToken(), true).verdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(store.cases(job.id(), 0)).isEmpty();
    }

    private Long problem() {
        return problems.saveAndFlush(Problem.builder().title("합").description("두 수").category("구현")
            .timeLimitMs(1000).memoryLimitMb(256).build()).getId();
    }
}

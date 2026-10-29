package com.example.cortebe.domain.judge;

import com.example.cortebe.domain.judge.dto.CreateJobRequest;
import com.example.cortebe.domain.judge.dto.CreateTestCaseRequest;
import com.example.cortebe.domain.judge.dto.JobResponse;
import com.example.cortebe.domain.judge.dto.UpdateTestCaseRequest;
import com.example.cortebe.domain.judge.exception.JudgeConflictException;
import com.example.cortebe.domain.judge.exception.JudgeForbiddenException;
import com.example.cortebe.domain.judge.exception.JudgeNotFoundException;
import com.example.cortebe.domain.judge.repository.JudgeJobRepository;
import com.example.cortebe.domain.judge.repository.TestCaseRepository;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.judge.runner.Verdict;
import com.example.cortebe.domain.judge.service.JobStore;
import com.example.cortebe.domain.judge.service.TestCaseService;
import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.domain.problem.service.ProblemService;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class JobStoreTests {
    @Autowired JobStore store;
    @Autowired TestCaseService cases;
    @Autowired ProblemRepository problems;
    @Autowired ProblemService problemService;
    @Autowired JudgeJobRepository jobs;
    @Autowired TestCaseRepository caseRepository;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void 실행입력은_고정하고_출력은_완료시에만_한번_저장한다() {
        // given
        Long id = problem();
        JobResponse created = store.create(null, new CreateJobRequest(id, Language.PYTHON, "code", "한글\n"), false);
        // when
        assertThat(store.claim(created.id()).stdin()).isEqualTo("한글\n");
        store.finishExecution(created.id(), Verdict.FINISHED, 12, "", "한글\n", "err");
        store.finishExecution(created.id(), Verdict.SYSTEM_ERROR, 0, "bad", "overwrite", "bad");
        // then
        JobResponse result = store.get(created.id(), null, created.accessToken(), false);
        assertThat(result.verdict()).isEqualTo(Verdict.FINISHED);
        assertThat(result.stdout()).isEqualTo("한글\n");
        assertThat(result.stderr()).isEqualTo("err");
        assertThat(jobs.findById(created.id()).orElseThrow().getStdin()).isNull();
    }

    @Test
    void 실행환경_오류로_종료해도_원본_입력은_지운다() {
        // given
        Long id = problem();
        JobResponse created = store.create(null, new CreateJobRequest(id, Language.PYTHON, "code", "private input"), false);
        store.claim(created.id());
        // when
        store.finish(created.id(), Verdict.SYSTEM_ERROR, 0, 0, "통신 실패");
        // then
        entityManager.clear();
        assertThat(jobs.findById(created.id()).orElseThrow().getStdin()).isNull();
    }

    @Test
    void 접수거절_취소_재시작_복구도_실행입력을_정리한다() {
        // given
        Long id = problem();
        var request = new CreateJobRequest(id, Language.PYTHON, "code", "private input");
        var rejected = store.create(null, request, false);
        var cancelled = store.create(null, request, false);
        var interrupted = store.create(null, request, false);
        store.claim(interrupted.id());
        // when
        store.reject(rejected.id());
        store.cancelQueued(cancelled.id());
        store.recover();
        entityManager.clear();
        // then
        for (Long jobId : java.util.List.of(rejected.id(), cancelled.id(), interrupted.id())) {
            assertThat(jobs.findById(jobId).orElseThrow().getStdin()).isNull();
        }
    }

    @Test
    void 재시작_복구는_실패_처리_시각도_갱신한다() {
        // given
        var created = store.create(null, new CreateJobRequest(problem(), Language.PYTHON, "code"), false);
        var oldTime = java.time.LocalDateTime.now().minusDays(1);
        jdbc.update("update judge_jobs set updated_at = ? where id = ?", java.sql.Timestamp.valueOf(oldTime), created.id());
        entityManager.clear();
        // when
        store.recover();
        entityManager.clear();
        // then
        var recovered = jobs.findById(created.id()).orElseThrow();
        assertThat(recovered.getVerdict()).isEqualTo(Verdict.SYSTEM_ERROR);
        assertThat(recovered.getUpdatedAt()).isAfter(oldTime.plusHours(23));
    }

    @Test
    void 실행입력이_UTF8_1MB를_넘으면_거절한다() {
        // given
        Long id = problem();
        // when & then
        assertThatThrownBy(() -> store.create(null,
            new CreateJobRequest(id, Language.PYTHON, "code", "가".repeat(350000)), false))
            .isInstanceOf(com.example.cortebe.global.error.exception.InvalidInputException.class);
    }

    @Test
    void 제출_스냅샷에는_선택한_언어의_메모리_제한을_고정한다() {
        // given
        Long id = problems.saveAndFlush(Problem.builder().title("합").description("두 수").category("구현")
            .timeLimitMs(1000).memoryLimitMb(256)
            .languageMemoryLimitsMb(java.util.Map.of(Language.PYTHON, 64)).build()).getId();
        // when
        JobResponse created = store.create(null, request(id), false);
        // then
        assertThat(store.claim(created.id()).snapshot().memoryLimitMb()).isEqualTo(64);
    }

    @Test
    void 스냅샷은_순서를_유지하며_8개씩_조회하고_완료후_삭제한다() {
        // given
        Long id = problem();
        for (int i = 8; i >= 0; i--) cases.create(id, new CreateTestCaseRequest("", String.valueOf(i), false, i));
        JobResponse job = store.create(null, request(id), true);
        // when & then
        assertThat(store.cases(job.id(), 0)).extracting(JobStore.Case::expectedOutput)
            .containsExactly("0", "1", "2", "3", "4", "5", "6", "7");
        assertThat(store.cases(job.id(), 1)).extracting(JobStore.Case::expectedOutput).containsExactly("8");
        store.claim(job.id());
        store.finish(job.id(), Verdict.ACCEPTED, 9, 10, null);
        assertThat(store.cases(job.id(), 0)).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 케이스_100개_제한을_동시_요청에서도_보호한다() throws Exception {
        // given
        Long id = problem();
        for (int i = 0; i < 99; i++) cases.create(id, new CreateTestCaseRequest("", "", false, i));
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<Boolean> create = () -> {
            start.await();
            try { cases.create(id, new CreateTestCaseRequest("", "", false, 99)); return true; }
            catch (JudgeConflictException exception) { return false; }
        };
        // when
        try (java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(create);
            var second = executor.submit(create);
            start.countDown();
            // then
            assertThat(java.util.List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                second.get(10, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            assertThat(caseRepository.countByProblemId(id)).isEqualTo(100);
        } finally { problemService.delete(id); }
    }

    @Test
    void 비회원_만료결과는_접근을_거절하고_정리한다() {
        // given
        Long id = problem();
        JobResponse created = store.create(null, request(id), false);
        jdbc.update("update judge_jobs set expires_at = ? where id = ?",
            java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusMinutes(1)), created.id());
        entityManager.clear();
        // when & then
        assertThatThrownBy(() -> store.get(created.id(), null, created.accessToken(), false))
            .isInstanceOf(JudgeNotFoundException.class);
        store.cleanup();
        assertThat(jobs.existsById(created.id())).isFalse();
    }

    @Test
    void 한_문제의_케이스는_100개까지만_허용한다() {
        // given
        Long id = problem();
        for (int i = 0; i < 100; i++) cases.create(id, new CreateTestCaseRequest("", "", false, i));
        // when & then
        assertThatThrownBy(() -> cases.create(id, new CreateTestCaseRequest("", "", false, 100)))
            .isInstanceOf(JudgeConflictException.class);
    }

    @Test
    void 비회원_결과는_비밀토큰이_필요하고_회원에게_연결되지_않는다() {
        // given
        Long problemId = problem();
        cases.create(problemId, new CreateTestCaseRequest("", "3\n", false, 0));
        JobResponse created = store.create(null, request(problemId), true);
        // when & then
        assertThat(created.accessToken()).isNotBlank();
        assertThatThrownBy(() -> store.get(created.id(), null, null, true)).isInstanceOf(JudgeForbiddenException.class);
        assertThatThrownBy(() -> store.get(created.id(), null, "wrong", true)).isInstanceOf(JudgeForbiddenException.class);
        assertThat(store.get(created.id(), null, created.accessToken(), true).verdict()).isEqualTo(Verdict.QUEUED);
        assertThat(jobs.findWithAuthorById(created.id()).orElseThrow().getAuthor()).isNull();
        assertThat(jobs.findById(created.id()).orElseThrow().getExpiresAt()).isNotNull();
    }

    @Test
    void 회원_결과는_본인만_조회할_수_있다() {
        // given
        Long problemId = problem();
        cases.create(problemId, new CreateTestCaseRequest("", "3\n", true, 0));
        User user = users.saveAndFlush(User.builder().email("judge@corte.test").password("password").nickname("회원").build());
        JobResponse created = store.create(user.getId(), request(problemId), true);
        // when & then
        assertThat(created.accessToken()).isNull();
        assertThat(store.get(created.id(), user.getId(), null, true).id()).isEqualTo(created.id());
        assertThatThrownBy(() -> store.get(created.id(), user.getId() + 1, null, true)).isInstanceOf(JudgeForbiddenException.class);
        assertThat(jobs.findById(created.id()).orElseThrow().getExpiresAt()).isNull();
    }

    @Test
    void 제출시_케이스와_제한을_고정하고_중복_작업을_실행하지_않는다() {
        // given
        Long problemId = problem();
        Long caseId = cases.create(problemId, new CreateTestCaseRequest("1 2\n", "3\n", false, 0)).id();
        JobResponse created = store.create(null, request(problemId), true);
        cases.update(problemId, caseId, new UpdateTestCaseRequest("4 5\n", "9\n", null, null));
        // when
        JobStore.Work work = store.claim(created.id());
        // then
        assertThat(store.cases(created.id(), 0).getFirst().expectedOutput()).isEqualTo("3\n");
        assertThat(work.snapshot().timeLimitMs()).isEqualTo(1000);
        assertThat(store.claim(created.id())).isNull();
        store.finish(created.id(), Verdict.ACCEPTED, 1, 15, null);
        store.finish(created.id(), Verdict.SYSTEM_ERROR, 0, 0, "중복");
        assertThat(store.get(created.id(), null, created.accessToken(), true).verdict()).isEqualTo(Verdict.ACCEPTED);
    }

    @Test
    void 케이스가_없는_문제는_실행만_가능하다() {
        // given
        Long id = problem();
        // when & then
        assertThatThrownBy(() -> store.create(null, request(id), true)).isInstanceOf(JudgeConflictException.class);
        JobResponse execution = store.create(null, request(id), false);
        assertThat(execution.accessToken()).isNotBlank();
        assertThat(execution.streamPath()).isNull();
    }

    @Test
    void 제출이_있는_문제는_삭제할_수_없다() {
        // given
        Long id = problem();
        cases.create(id, new CreateTestCaseRequest("", "3\n", true, 0));
        store.create(null, request(id), true);
        // when & then
        assertThatThrownBy(() -> problemService.delete(id)).isInstanceOf(JudgeConflictException.class);
    }

    @Test
    void 제출없는_문제를_삭제하면_예제도_함께_삭제한다() {
        // given
        Long id = problem();
        cases.create(id, new CreateTestCaseRequest("", "3\n", true, 0));
        // when
        problemService.delete(id);
        problems.flush();
        // then
        assertThat(caseRepository.countByProblemId(id)).isZero();
    }

    private Long problem() {
        return problems.saveAndFlush(Problem.builder().title("합").description("두 수").category("구현")
            .timeLimitMs(1000).memoryLimitMb(256).build()).getId();
    }

    private CreateJobRequest request(Long id) { return new CreateJobRequest(id, Language.PYTHON, "print(3)"); }
}

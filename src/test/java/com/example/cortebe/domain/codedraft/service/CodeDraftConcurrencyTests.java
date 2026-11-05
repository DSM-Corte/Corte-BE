package com.example.cortebe.domain.codedraft.service;

import com.example.cortebe.domain.codedraft.dto.SaveCodeDraftRequest;
import com.example.cortebe.domain.codedraft.exception.CodeDraftProblemNotFoundException;
import com.example.cortebe.domain.codedraft.repository.CodeDraftRepository;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.domain.problem.service.ProblemService;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CodeDraftConcurrencyTests {

    @Autowired private CodeDraftService service;
    @Autowired private CodeDraftRepository drafts;
    @Autowired private UserRepository users;
    @Autowired private ProblemRepository problems;
    @Autowired private ProblemService problemService;

    @Test
    void 동시에_처음_저장해도_기록은_하나이고_다음_저장이_덮어쓴다() throws Exception {
        // given
        User user = user();
        Problem problem = problem();
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // when
            var first = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return service.save(user.getId(), problem.getId(), Language.JAVA, new SaveCodeDraftRequest("첫 요청"));
            });
            var second = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return service.save(user.getId(), problem.getId(), Language.JAVA, new SaveCodeDraftRequest("둘째 요청"));
            });
            var results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            // then
            assertThat(results).extracting(CodeDraftService.SaveResult::created).containsExactlyInAnyOrder(true, false);
            assertThat(drafts.count()).isEqualTo(1);
            assertThat(service.get(user.getId(), problem.getId(), Language.JAVA).code()).isIn("첫 요청", "둘째 요청");
            service.save(user.getId(), problem.getId(), Language.JAVA, new SaveCodeDraftRequest("마지막 요청"));
            assertThat(service.get(user.getId(), problem.getId(), Language.JAVA).code()).isEqualTo("마지막 요청");
            assertThat(drafts.count()).isEqualTo(1);
        } finally {
            problemService.delete(problem.getId());
            users.deleteById(user.getId());
        }
    }

    @Test
    void 저장과_문제_삭제가_겹쳐도_코드_기록이_남지_않는다() throws Exception {
        // given
        User user = user();
        Problem problem = problem();
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // when
            var save = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    service.save(user.getId(), problem.getId(), Language.JAVA, new SaveCodeDraftRequest("저장 코드"));
                    return "저장 성공";
                } catch (CodeDraftProblemNotFoundException exception) {
                    return "문제 삭제됨";
                }
            });
            var delete = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                problemService.delete(problem.getId());
                return "삭제 성공";
            });
            // then
            assertThat(save.get(15, TimeUnit.SECONDS)).isIn("저장 성공", "문제 삭제됨");
            assertThat(delete.get(15, TimeUnit.SECONDS)).isEqualTo("삭제 성공");
            assertThat(problems.existsById(problem.getId())).isFalse();
            assertThat(drafts.count()).isZero();
        } finally {
            if (problems.existsById(problem.getId())) problemService.delete(problem.getId());
            users.deleteById(user.getId());
        }
    }

    private User user() {
        return users.save(User.builder().email(UUID.randomUUID() + "@draft.test").nickname("회원").password("password").build());
    }

    private Problem problem() {
        return problems.save(Problem.builder().title("문제").description("설명").category("구현")
            .memoryLimitMb(256).timeLimitMs(1000).build());
    }
}

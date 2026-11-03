package com.example.cortebe.domain.judge.repository;

import com.example.cortebe.domain.judge.entity.JudgeJob;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.judge.runner.Verdict;
import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class JudgeJobRepositoryTests {

    @Autowired
    private JudgeJobRepository judgeJobRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Test
    void 내_정답_제출만_조회한다() {
        // given
        User me = userRepository.save(new User("me@corte.com", "password", "me"));
        User other = userRepository.save(new User("other@corte.com", "password", "other"));
        Problem problem = problemRepository.save(newProblem());

        saveJob(problem, me, true, Verdict.ACCEPTED);      // 내 정답 제출 (이것만 나와야 한다)
        saveJob(problem, me, true, Verdict.WRONG_ANSWER);  // 내 오답 제출
        saveJob(problem, me, false, Verdict.ACCEPTED);     // 내 직접 실행
        saveJob(problem, other, true, Verdict.ACCEPTED);   // 다른 사람 정답 제출

        // when
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        List<JudgeJobRepository.SolvedSubmission> result =
            judgeJobRepository.findSolvedSubmissions(me.getId(), Verdict.ACCEPTED, yesterday);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProblemId()).isEqualTo(problem.getId());
    }

    @Test
    void from_이전에_낸_제출은_조회하지_않는다() {
        // given
        User me = userRepository.save(new User("me@corte.com", "password", "me"));
        Problem problem = problemRepository.save(newProblem());
        saveJob(problem, me, true, Verdict.ACCEPTED);

        // when
        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1);
        List<JudgeJobRepository.SolvedSubmission> result =
            judgeJobRepository.findSolvedSubmissions(me.getId(), Verdict.ACCEPTED, tomorrow);

        // then
        assertThat(result).isEmpty();
    }

    private Problem newProblem() {
        return Problem.builder()
            .title("두 수의 합")
            .description("두 수를 더한다")
            .category("구현")
            .memoryLimitMb(256)
            .timeLimitMs(1000)
            .build();
    }

    // 채점 기록을 저장하고 판정을 verdict로 바꾼다
    private void saveJob(Problem problem, User author, boolean submission, Verdict verdict) {
        JudgeJob job = judgeJobRepository.save(JudgeJob.builder()
            .problem(problem)
            .author(author)
            .submission(submission)
            .language(Language.PYTHON)
            .code("print(1)")
            .snapshot("")
            .tokenHash("hash")
            .totalCount(1)
            .build());
        judgeJobRepository.finish(job.getId(), Verdict.QUEUED, verdict, 1, 10, "", LocalDateTime.now());
    }
}

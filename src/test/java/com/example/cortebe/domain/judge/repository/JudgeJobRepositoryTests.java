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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class JudgeJobRepositoryTests {

    @Autowired
    private JudgeJobRepository jobRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 제출_목록은_본인의_제출만_문제_제목과_함께_최신순으로_가져온다() {
        // given
        User me = saveUser("me");
        User other = saveUser("other");
        Problem first = saveProblem("합");
        Problem second = saveProblem("곱");
        JudgeJob older = save(first, me, true);
        JudgeJob newer = save(second, me, true);
        save(first, me, false);
        save(first, other, true);
        save(first, null, true);

        // when
        Page<JudgeJobRepository.Summary> page = jobRepository.findSummaries(me.getId(), null, latest());

        // then
        assertThat(page.getContent()).extracting(JudgeJobRepository.Summary::getId)
            .containsExactly(newer.getId(), older.getId());
        assertThat(page.getTotalElements()).isEqualTo(2);
        JudgeJobRepository.Summary summary = page.getContent().getFirst();
        assertThat(summary.getProblemId()).isEqualTo(second.getId());
        assertThat(summary.getProblemTitle()).isEqualTo("곱");
        assertThat(summary.getLanguage()).isEqualTo(Language.PYTHON);
        assertThat(summary.getVerdict()).isEqualTo(Verdict.QUEUED);
        assertThat(summary.getCreatedAt()).isNotNull();
    }

    @Test
    void 문제_ID를_주면_그_문제의_제출만_가져온다() {
        // given
        User me = saveUser("me");
        Problem first = saveProblem("합");
        Problem second = saveProblem("곱");
        JudgeJob target = save(first, me, true);
        save(second, me, true);

        // when
        Page<JudgeJobRepository.Summary> page = jobRepository.findSummaries(me.getId(), first.getId(), latest());

        // then
        assertThat(page.getContent()).extracting(JudgeJobRepository.Summary::getId).containsExactly(target.getId());
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(jobRepository.findSummaries(me.getId(), 999999L, latest()).getContent()).isEmpty();
    }

    private PageRequest latest() {
        return PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"));
    }

    private JudgeJob save(Problem problem, User author, boolean submission) {
        return jobRepository.save(JudgeJob.builder().problem(problem).author(author).submission(submission)
            .language(Language.PYTHON).code("print(3)").snapshot("{}").tokenHash("hash").totalCount(1).build());
    }

    private Problem saveProblem(String title) {
        return problemRepository.save(Problem.builder().title(title).description("설명").category("구현")
            .timeLimitMs(1000).memoryLimitMb(256).build());
    }

    private User saveUser(String nickname) {
        return userRepository.save(User.builder()
            .email(nickname + "@corte.com").password("password").nickname(nickname).build());
    }
}

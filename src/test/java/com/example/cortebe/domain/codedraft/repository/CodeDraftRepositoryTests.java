package com.example.cortebe.domain.codedraft.repository;

import com.example.cortebe.domain.codedraft.entity.CodeDraft;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class CodeDraftRepositoryTests {

    @Autowired private CodeDraftRepository drafts;
    @Autowired private UserRepository users;
    @Autowired private ProblemRepository problems;
    @Autowired private EntityManager entityManager;

    @Test
    void 같은_회원_문제_언어는_DB에서도_중복을_막는다() {
        // given
        User user = user();
        Problem problem = problem();
        drafts.saveAndFlush(draft(user, problem, "첫 코드"));
        // when & then
        assertThatThrownBy(() -> drafts.saveAndFlush(draft(user, problem, "중복 코드")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 최대_길이의_유니코드와_시각을_DB에_저장한다() {
        // given
        User user = user();
        Problem problem = problem();
        String code = "한".repeat(102400);
        drafts.saveAndFlush(draft(user, problem, code));
        entityManager.clear();
        // when
        CodeDraft loaded = drafts.findByUserIdAndProblemIdAndLanguage(user.getId(), problem.getId(), Language.JAVA).orElseThrow();
        // then
        assertThat(loaded.getCode()).isEqualTo(code);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    private User user() {
        return users.save(User.builder().email("repository@draft.test").nickname("회원").password("password").build());
    }

    private Problem problem() {
        return problems.save(Problem.builder().title("문제").description("설명").category("구현")
            .memoryLimitMb(256).timeLimitMs(1000).build());
    }

    private CodeDraft draft(User user, Problem problem, String code) {
        return CodeDraft.builder().user(user).problem(problem).language(Language.JAVA).code(code).build();
    }
}

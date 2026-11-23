package com.example.cortebe.domain.user.repository;

import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.domain.user.entity.ProblemReward;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class ProblemRewardRepositoryTests {

    @Autowired ProblemRewardRepository rewards;
    @Autowired UserRepository users;
    @Autowired ProblemRepository problems;

    @Test
    void 회원과_문제가_같은_지급_기록은_유니크_제약으로_막는다() {
        // given
        User user = users.saveAndFlush(User.builder().email("unique@tier.test").password("password").nickname("me").build());
        Problem problem = problems.saveAndFlush(Problem.builder().title("합").description("더하기").category("구현")
            .memoryLimitMb(256).timeLimitMs(1000).build());
        rewards.saveAndFlush(ProblemReward.builder().user(user).problem(problem).points(10L).build());
        // when & then
        assertThat(rewards.sumPoints(user.getId())).isEqualTo(10);
        assertThatThrownBy(() -> rewards.saveAndFlush(ProblemReward.builder()
            .user(user).problem(problem).points(10L).build())).isInstanceOf(DataIntegrityViolationException.class);
    }
}

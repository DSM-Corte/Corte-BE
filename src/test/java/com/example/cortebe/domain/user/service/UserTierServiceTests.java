package com.example.cortebe.domain.user.service;

import com.example.cortebe.domain.judge.entity.JudgeJob;
import com.example.cortebe.domain.judge.repository.JudgeJobRepository;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.judge.runner.Verdict;
import com.example.cortebe.domain.judge.service.JobStore;
import com.example.cortebe.domain.problem.entity.Difficulty;
import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.entity.UserTier;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.domain.user.repository.ProblemRewardRepository;
import com.example.cortebe.global.security.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class UserTierServiceTests {

    @Autowired UserTierService tiers;
    @Autowired JobStore store;
    @Autowired UserRepository users;
    @Autowired ProblemRepository problems;
    @Autowired JudgeJobRepository jobs;
    @Autowired ProblemRewardRepository rewards;
    @Autowired TransactionTemplate transactions;

    @ParameterizedTest
    @CsvSource({"BRONZE_5,10", "BRONZE_1,10", "SILVER_5,20", "SILVER_1,20",
        "GOLD_5,30", "GOLD_1,30", "PLATINUM_5,40", "PLATINUM_1,40",
        "DIAMOND_5,50", "DIAMOND_1,50", "RUBY_5,60", "RUBY_1,60", "UNRATED,0"})
    void 최초_정답은_문제_난이도별_점수를_지급한다(Difficulty difficulty, long points) {
        // given
        User me = user("me");
        JudgeJob job = job(me, problem(difficulty), true);
        store.claim(job.getId());
        // when
        store.finish(job.getId(), Verdict.ACCEPTED, 1, 10, null);
        // then
        assertThat(rewards.sumPoints(me.getId())).isEqualTo(points);
        assertThat(rewards.findByUserIdAndProblemId(me.getId(), job.getProblem().getId()).orElseThrow().getPoints())
            .isEqualTo(points);
        assertThat(tiers.get(me.getId()).score()).isEqualTo(points);
    }

    @ParameterizedTest
    @CsvSource({"false,true", "true,false", "false,false"})
    void 비회원과_단순_실행의_정답_완료에는_지급_기록이_없다(boolean member, boolean submission) {
        // given
        User me = user("me");
        JudgeJob job = job(member ? me : null, problem(Difficulty.GOLD_3), submission);
        long before = rewards.count();
        store.claim(job.getId());
        // when
        store.finish(job.getId(), Verdict.ACCEPTED, 1, 10, null);
        // then
        assertThat(rewards.count()).isEqualTo(before);
        assertThat(rewards.sumPoints(me.getId())).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = Verdict.class, names = {"ACCEPTED", "QUEUED", "RUNNING"}, mode = EnumSource.Mode.EXCLUDE)
    void 정답이_아닌_완료_판정은_점수를_지급하지_않는다(Verdict verdict) {
        // given
        User me = user("me");
        JudgeJob job = job(me, problem(Difficulty.RUBY_1), true);
        store.claim(job.getId());
        // when
        store.finish(job.getId(), verdict, 0, 10, null);
        // then
        assertThat(rewards.findByUserIdAndProblemId(me.getId(), job.getProblem().getId())).isEmpty();
        assertThat(tiers.get(me.getId()).score()).isZero();
    }

    @Test
    void 기존_중복_정답을_한번만_반영하고_반복_조회해도_점수는_유지한다() {
        // given
        User me = user("me");
        Problem problem = problem(Difficulty.GOLD_3);
        legacyAccepted(job(me, problem, true));
        legacyAccepted(job(me, problem, true));
        // when & then
        assertThat(tiers.get(me.getId()).score()).isEqualTo(30);
        assertThat(tiers.get(me.getId()).score()).isEqualTo(30);
        assertThat(tiers.get(me.getId()).tier()).isEqualTo(UserTier.BRONZE_2);
        assertThat(tiers.get(me.getId()).nextTier()).isEqualTo(UserTier.BRONZE_1);
        assertThat(tiers.get(me.getId()).pointsToNextTier()).isEqualTo(30);
    }

    @Test
    void 콜백_중복과_다른_언어의_재제출과_난이도_변경은_추가_지급하지_않는다() {
        // given
        User me = user("me");
        Problem problem = problem(Difficulty.BRONZE_3);
        JudgeJob first = job(me, problem, true);
        store.claim(first.getId());
        store.finish(first.getId(), Verdict.ACCEPTED, 1, 10, null);
        problems.findById(problem.getId()).orElseThrow().updateDifficulty(Difficulty.RUBY_1);
        problems.flush();
        JudgeJob second = jobs.saveAndFlush(JudgeJob.builder().problem(problem).author(me).submission(true)
            .language(Language.JAVA).code("code").snapshot("{\"timeLimitMs\":1000,\"memoryLimitMb\":256}")
            .tokenHash("hash").totalCount(1).build());
        store.claim(second.getId());
        // when
        store.finish(first.getId(), Verdict.ACCEPTED, 1, 10, null);
        store.finish(second.getId(), Verdict.ACCEPTED, 1, 10, null);
        // then
        assertThat(tiers.get(me.getId()).score()).isEqualTo(10);
    }

    @Test
    void 미지정_문제는_난이도_지정만으로_지급하지_않고_새_정답에서_지급한다() {
        // given
        User me = user("me");
        Problem problem = problem(Difficulty.UNRATED);
        JudgeJob first = job(me, problem, true);
        store.claim(first.getId());
        store.finish(first.getId(), Verdict.ACCEPTED, 1, 10, null);
        assertThat(tiers.get(me.getId()).score()).isZero();
        problems.findById(problem.getId()).orElseThrow().updateDifficulty(Difficulty.SILVER_2);
        problems.flush();
        // when & then
        assertThat(tiers.get(me.getId()).score()).isZero();
        store.finish(first.getId(), Verdict.ACCEPTED, 1, 10, null);
        assertThat(tiers.get(me.getId()).score()).isZero();
        JudgeJob second = job(me, problems.findById(problem.getId()).orElseThrow(), true);
        store.claim(second.getId());
        store.finish(second.getId(), Verdict.ACCEPTED, 1, 10, null);
        assertThat(tiers.get(me.getId()).score()).isEqualTo(20);
    }

    @Test
    void 기존_기록은_백개를_넘어도_빠짐없이_반영한다() {
        // given
        User me = user("me");
        for (int index = 0; index < 101; index++) {
            legacyAccepted(job(me, problem(Difficulty.BRONZE_5), true));
        }
        // when & then
        assertThat(tiers.get(me.getId()).score()).isEqualTo(1010);
        assertThat(tiers.get(me.getId()).score()).isEqualTo(1010);
    }

    @Test
    void 다른_회원과_비회원과_실행과_오답은_지급하지_않는다() {
        // given
        User me = user("me");
        Problem problem = problem(Difficulty.RUBY_1);
        legacyAccepted(job(user("other"), problem, true));
        legacyAccepted(job(null, problem, true));
        legacyAccepted(job(me, problem, false));
        JudgeJob wrong = job(me, problem, true);
        store.claim(wrong.getId());
        // when
        store.finish(wrong.getId(), Verdict.WRONG_ANSWER, 0, 10, null);
        // then
        assertThat(tiers.get(me.getId()).score()).isZero();
        assertThat(tiers.get(me.getId()).tier()).isEqualTo(UserTier.BRONZE_3);
        assertThat(tiers.get(me.getId()).pointsToNextTier()).isEqualTo(30);
    }

    @Test
    void 토큰의_회원이_없으면_인증_오류다() {
        // when & then
        assertThatThrownBy(() -> tiers.get(Long.MAX_VALUE)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 동시_정답과_기존_기록_조회는_한번만_지급한다() throws Exception {
        // given
        User me = user("concurrent");
        Problem problem = problem(Difficulty.GOLD_3);
        JudgeJob first = job(me, problem, true);
        JudgeJob second = job(me, problem, true);
        store.claim(first.getId());
        store.claim(second.getId());
        CountDownLatch start = new CountDownLatch(1);
        // when
        try (var executor = Executors.newFixedThreadPool(3)) {
            var firstResult = executor.submit(() -> {
                start.await();
                store.finish(first.getId(), Verdict.ACCEPTED, 1, 10, null);
                return null;
            });
            var secondResult = executor.submit(() -> {
                start.await();
                store.finish(second.getId(), Verdict.ACCEPTED, 1, 10, null);
                return null;
            });
            var readResult = executor.submit(() -> {
                start.await();
                return tiers.get(me.getId());
            });
            start.countDown();
            firstResult.get(20, TimeUnit.SECONDS);
            secondResult.get(20, TimeUnit.SECONDS);
            readResult.get(20, TimeUnit.SECONDS);
            // then
            assertThat(tiers.get(me.getId()).score()).isEqualTo(30);
            assertThat(rewards.findByUserIdAndProblemId(me.getId(), problem.getId())).isPresent();
        } finally {
            transactions.executeWithoutResult(status -> {
                rewards.findByUserIdAndProblemId(me.getId(), problem.getId()).ifPresent(rewards::delete);
                rewards.flush();
                jobs.deleteAllById(List.of(first.getId(), second.getId()));
                jobs.flush();
                problems.deleteById(problem.getId());
                users.deleteById(me.getId());
            });
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 채점_완료가_롤백되면_점수도_함께_롤백된다() {
        // given
        User me = user("rollback");
        Problem problem = problem(Difficulty.SILVER_1);
        JudgeJob job = job(me, problem, true);
        store.claim(job.getId());
        try {
            // when
            transactions.executeWithoutResult(status -> {
                store.finish(job.getId(), Verdict.ACCEPTED, 1, 10, null);
                assertThat(rewards.sumPoints(me.getId())).isEqualTo(20);
                status.setRollbackOnly();
            });
            // then
            assertThat(jobs.findById(job.getId()).orElseThrow().getVerdict()).isEqualTo(Verdict.RUNNING);
            assertThat(rewards.findByUserIdAndProblemId(me.getId(), problem.getId())).isEmpty();
            assertThat(tiers.get(me.getId()).score()).isZero();
        } finally {
            transactions.executeWithoutResult(status -> {
                rewards.findByUserIdAndProblemId(me.getId(), problem.getId()).ifPresent(rewards::delete);
                rewards.flush();
                jobs.deleteById(job.getId());
                jobs.flush();
                problems.deleteById(problem.getId());
                users.deleteById(me.getId());
            });
        }
    }

    private User user(String name) {
        return users.saveAndFlush(User.builder().email(name + "@tier.test").password("password").nickname(name).build());
    }

    private Problem problem(Difficulty difficulty) {
        return problems.saveAndFlush(Problem.builder().title("합").description("더하기").category("구현")
            .difficulty(difficulty).memoryLimitMb(256).timeLimitMs(1000).build());
    }

    private JudgeJob job(User user, Problem problem, boolean submission) {
        return jobs.saveAndFlush(JudgeJob.builder().problem(problem).author(user).submission(submission)
            .language(Language.PYTHON).code("code").snapshot("{\"timeLimitMs\":1000,\"memoryLimitMb\":256}")
            .tokenHash("hash").totalCount(1).build());
    }

    private void legacyAccepted(JudgeJob job) {
        jobs.finish(job.getId(), Verdict.QUEUED, Verdict.ACCEPTED, 1, 10, null, LocalDateTime.now());
    }
}

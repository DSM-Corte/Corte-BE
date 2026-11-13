package com.example.cortebe.domain.review.repository;

import com.example.cortebe.domain.judge.entity.JudgeJob;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.domain.review.entity.ReviewRequest;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class ReviewRequestRepositoryTests {

    private static final PageRequest PAGE = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"));

    @Autowired
    private ReviewRequestRepository reviewRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private EntityManager entityManager;

    private User requester;
    private User other;
    private Problem problem;

    @BeforeEach
    void setUp() {
        requester = userRepository.save(new User("requester@corte.com", "password", "요청자"));
        other = userRepository.save(new User("other@corte.com", "password", "리뷰어"));
        problem = problemRepository.save(Problem.builder().title("문제").description("설명").category("구현")
            .memoryLimitMb(256).timeLimitMs(1000).build());
    }

    @Test
    void 목록은_닫히지_않은_요청과_내가_요청한_닫힌_요청만_보여준다() {
        // given
        saveRequest(requester, false);
        ReviewRequest closedMine = saveRequest(requester, true);
        saveRequest(other, true);

        // when
        Page<ReviewRequestRepository.Summary> forRequester = reviewRequestRepository.findSummaries(requester.getId(), null, PAGE);
        Page<ReviewRequestRepository.Summary> forOther = reviewRequestRepository.findSummaries(other.getId(), null, PAGE);

        // then
        assertThat(forRequester.getContent()).hasSize(2).extracting(ReviewRequestRepository.Summary::getId)
            .contains(closedMine.getId());
        assertThat(forRequester.getTotalElements()).isEqualTo(2);
        assertThat(forOther.getContent()).hasSize(2);
        assertThat(forOther.getContent()).extracting(ReviewRequestRepository.Summary::getId)
            .doesNotContain(closedMine.getId());
    }

    @Test
    void 목록은_문제_ID로_거를_수_있다() {
        // given
        saveRequest(requester, false);
        Problem another = problemRepository.save(Problem.builder().title("다른 문제").description("설명").category("구현")
            .memoryLimitMb(256).timeLimitMs(1000).build());
        ReviewRequest onAnother = saveRequest(requester, false, another);

        // when
        Page<ReviewRequestRepository.Summary> page = reviewRequestRepository.findSummaries(other.getId(), another.getId(), PAGE);

        // then
        assertThat(page.getContent()).extracting(ReviewRequestRepository.Summary::getId).containsExactly(onAnother.getId());
        assertThat(page.getContent().getFirst().getProblemTitle()).isEqualTo("다른 문제");
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void 닫기는_열린_요청에서만_한_번_성공한다() {
        // given
        ReviewRequest request = saveRequest(requester, false);

        // when
        int first = reviewRequestRepository.close(request.getId(), LocalDateTime.now());
        int second = reviewRequestRepository.close(request.getId(), LocalDateTime.now());

        // then
        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(reviewRequestRepository.findById(request.getId()).orElseThrow().isClosed()).isTrue();
    }

    @Test
    void 같은_제출로_두_번_요청하면_uk_review_requests_submission_id_제약에_걸린다() {
        // given
        JudgeJob submission = saveSubmission(problem);
        reviewRequestRepository.saveAndFlush(build(submission, requester, problem));

        // when & then
        assertThatThrownBy(() -> reviewRequestRepository.saveAndFlush(build(submission, requester, problem)))
            .isInstanceOf(DataIntegrityViolationException.class)
            .cause()
            .isInstanceOfSatisfying(ConstraintViolationException.class, cause ->
                assertThat(cause.getConstraintName()).containsIgnoringCase("uk_review_requests_submission_id"));
    }

    private ReviewRequest saveRequest(User owner, boolean closed) {
        return saveRequest(owner, closed, problem);
    }

    private ReviewRequest saveRequest(User owner, boolean closed, Problem target) {
        ReviewRequest request = reviewRequestRepository.saveAndFlush(build(saveSubmission(target), owner, target));
        if (closed) {
            reviewRequestRepository.close(request.getId(), LocalDateTime.now());
            entityManager.clear();
        }
        return request;
    }

    private ReviewRequest build(JudgeJob submission, User owner, Problem target) {
        return ReviewRequest.builder().submission(submission).requester(owner).problem(target)
            .language(Language.PYTHON).code("print(1)\n").build();
    }

    private JudgeJob saveSubmission(Problem target) {
        JudgeJob job = JudgeJob.builder().problem(target).author(requester).submission(true).language(Language.PYTHON)
            .code("print(1)\n").snapshot("{}").tokenHash("x".repeat(64)).totalCount(1).build();
        entityManager.persist(job);
        return job;
    }
}

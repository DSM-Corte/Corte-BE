package com.example.cortebe.domain.review.service;

import com.example.cortebe.domain.judge.entity.JudgeJob;
import com.example.cortebe.domain.judge.repository.JudgeJobRepository;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.judge.runner.Verdict;
import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.review.dto.ApplyRequest;
import com.example.cortebe.domain.review.dto.ApplyResponse;
import com.example.cortebe.domain.review.dto.CreateReviewRequestRequest;
import com.example.cortebe.domain.review.dto.ReviewRequestPageResponse;
import com.example.cortebe.domain.review.dto.ReviewRequestResponse;
import com.example.cortebe.domain.review.entity.ReviewRequest;
import com.example.cortebe.domain.review.exception.DuplicateReviewRequestException;
import com.example.cortebe.domain.review.exception.InvalidApplySelectionException;
import com.example.cortebe.domain.review.exception.RequesterOnlyException;
import com.example.cortebe.domain.review.exception.ReviewCommentNotFoundException;
import com.example.cortebe.domain.review.exception.ReviewRequestClosedException;
import com.example.cortebe.domain.review.exception.ReviewRequestNotFoundException;
import com.example.cortebe.domain.review.exception.SubmissionForbiddenException;
import com.example.cortebe.domain.review.exception.SubmissionNotAcceptedException;
import com.example.cortebe.domain.review.exception.SubmissionNotFoundException;
import com.example.cortebe.domain.review.exception.SuggestionMissingException;
import com.example.cortebe.domain.review.repository.LineCommentRepository;
import com.example.cortebe.domain.review.repository.ReviewRequestRepository;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.error.exception.InvalidInputException;
import com.example.cortebe.global.security.exception.UnauthorizedException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.example.cortebe.domain.review.ReviewFixtures.CODE;
import static com.example.cortebe.domain.review.ReviewFixtures.lineComment;
import static com.example.cortebe.domain.review.ReviewFixtures.problem;
import static com.example.cortebe.domain.review.ReviewFixtures.request;
import static com.example.cortebe.domain.review.ReviewFixtures.submission;
import static com.example.cortebe.domain.review.ReviewFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ReviewRequestServiceTests {

    @Mock
    private ReviewRequestRepository reviewRequestRepository;

    @Mock
    private JudgeJobRepository judgeJobRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LineCommentRepository lineCommentRepository;

    private ReviewRequestService service;

    @BeforeEach
    void setUp() {
        service = new ReviewRequestService(reviewRequestRepository, judgeJobRepository, userRepository,
            lineCommentRepository, new ReviewRequestFinder(reviewRequestRepository));
    }

    @Test
    void 본인의_정답_제출이면_리뷰_요청을_만든다() {
        // given
        User requester = user(1L);
        Problem problem = problem(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(requester));
        given(judgeJobRepository.findWithAuthorById(10L))
            .willReturn(Optional.of(submission(10L, requester, problem, Verdict.ACCEPTED)));
        given(reviewRequestRepository.saveAndFlush(any(ReviewRequest.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        ReviewRequestResponse response = service.create(1L, new CreateReviewRequestRequest(10L));

        // then
        assertThat(response.code()).isEqualTo(CODE);
        assertThat(response.language()).isEqualTo(Language.JAVA);
        assertThat(response.requesterId()).isEqualTo(1L);
        assertThat(response.closed()).isFalse();
    }

    @Test
    void 회원이_없으면_리뷰_요청을_만들지_않는다() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.create(1L, new CreateReviewRequestRequest(10L)))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void 제출이_없으면_리뷰_요청을_만들지_않는다() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
        given(judgeJobRepository.findWithAuthorById(10L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.create(1L, new CreateReviewRequestRequest(10L)))
            .isInstanceOf(SubmissionNotFoundException.class);
    }

    @Test
    void 직접_실행_결과는_제출이_아니므로_리뷰_요청을_만들지_않는다() {
        // given
        User requester = user(1L);
        JudgeJob execution = submission(10L, requester, problem(1L), Verdict.FINISHED);
        org.springframework.test.util.ReflectionTestUtils.setField(execution, "submission", false);
        given(userRepository.findById(1L)).willReturn(Optional.of(requester));
        given(judgeJobRepository.findWithAuthorById(10L)).willReturn(Optional.of(execution));

        // when & then
        assertThatThrownBy(() -> service.create(1L, new CreateReviewRequestRequest(10L)))
            .isInstanceOf(SubmissionNotFoundException.class);
    }

    @Test
    void 다른_회원의_제출이면_리뷰_요청을_만들지_않는다() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
        given(judgeJobRepository.findWithAuthorById(10L))
            .willReturn(Optional.of(submission(10L, user(2L), problem(1L), Verdict.ACCEPTED)));

        // when & then
        assertThatThrownBy(() -> service.create(1L, new CreateReviewRequestRequest(10L)))
            .isInstanceOf(SubmissionForbiddenException.class);
    }

    @Test
    void 비회원_제출이면_리뷰_요청을_만들지_않는다() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
        given(judgeJobRepository.findWithAuthorById(10L))
            .willReturn(Optional.of(submission(10L, null, problem(1L), Verdict.ACCEPTED)));

        // when & then
        assertThatThrownBy(() -> service.create(1L, new CreateReviewRequestRequest(10L)))
            .isInstanceOf(SubmissionForbiddenException.class);
    }

    @Test
    void 정답이_아닌_제출이면_리뷰_요청을_만들지_않는다() {
        // given
        User requester = user(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(requester));
        given(judgeJobRepository.findWithAuthorById(10L))
            .willReturn(Optional.of(submission(10L, requester, problem(1L), Verdict.WRONG_ANSWER)));

        // when & then
        assertThatThrownBy(() -> service.create(1L, new CreateReviewRequestRequest(10L)))
            .isInstanceOf(SubmissionNotAcceptedException.class);
        then(reviewRequestRepository).should(never()).saveAndFlush(any());
    }

    @Test
    void 저장할_때_제출_유니크_제약에_걸리면_중복_요청_예외를_던진다() {
        // given
        User requester = user(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(requester));
        given(judgeJobRepository.findWithAuthorById(10L))
            .willReturn(Optional.of(submission(10L, requester, problem(1L), Verdict.ACCEPTED)));
        given(reviewRequestRepository.saveAndFlush(any())).willThrow(integrityError(
            ConstraintViolationException.ConstraintKind.UNIQUE,
            "PUBLIC.UK_REVIEW_REQUESTS_SUBMISSION_ID INDEX PUBLIC.UK_REVIEW_REQUESTS_SUBMISSION_ID_INDEX_4 ON PUBLIC.REVIEW_REQUESTS(SUBMISSION_ID)"));

        // when & then
        assertThatThrownBy(() -> service.create(1L, new CreateReviewRequestRequest(10L)))
            .isInstanceOf(DuplicateReviewRequestException.class);
    }

    @Test
    void 제출_유니크_제약이_아닌_무결성_오류는_그대로_던진다() {
        // given
        User requester = user(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(requester));
        given(judgeJobRepository.findWithAuthorById(10L))
            .willReturn(Optional.of(submission(10L, requester, problem(1L), Verdict.ACCEPTED)));
        given(reviewRequestRepository.saveAndFlush(any())).willThrow(integrityError(
            ConstraintViolationException.ConstraintKind.UNIQUE, "uk_users_email"));

        // when & then
        assertThatThrownBy(() -> service.create(1L, new CreateReviewRequestRequest(10L)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 이름이_같아도_유니크_위반이_아니면_그대로_던진다() {
        // given
        User requester = user(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(requester));
        given(judgeJobRepository.findWithAuthorById(10L))
            .willReturn(Optional.of(submission(10L, requester, problem(1L), Verdict.ACCEPTED)));
        given(reviewRequestRepository.saveAndFlush(any())).willThrow(integrityError(
            ConstraintViolationException.ConstraintKind.NOT_NULL, "uk_review_requests_submission_id"));

        // when & then
        assertThatThrownBy(() -> service.create(1L, new CreateReviewRequestRequest(10L)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 요청자는_닫힌_요청도_조회한다() {
        // given
        given(reviewRequestRepository.findWithRequesterById(5L)).willReturn(Optional.of(request(5L, user(1L), true)));

        // when
        ReviewRequestResponse response = service.get(1L, 5L);

        // then
        assertThat(response.closed()).isTrue();
        assertThat(response.problemId()).isEqualTo(1L);
    }

    @Test
    void 리뷰어는_닫힌_요청을_조회할_수_없다() {
        // given
        given(reviewRequestRepository.findWithRequesterById(5L)).willReturn(Optional.of(request(5L, user(1L), true)));

        // when & then
        assertThatThrownBy(() -> service.get(2L, 5L)).isInstanceOf(ReviewRequestNotFoundException.class);
    }

    @Test
    void 없는_요청은_조회할_수_없다() {
        // given
        given(reviewRequestRepository.findWithRequesterById(5L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.get(1L, 5L)).isInstanceOf(ReviewRequestNotFoundException.class);
    }

    @Test
    void 페이지_범위를_벗어나면_목록_조회에_실패한다() {
        // when & then
        assertThatThrownBy(() -> service.list(1L, null, 0, 101)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> service.list(1L, null, -1, 20)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void 목록은_요약과_페이지_정보를_돌려준다() {
        // given
        ReviewRequestRepository.Summary summary = new ReviewRequestRepository.Summary() {
            public Long getId() { return 5L; }
            public Long getProblemId() { return 1L; }
            public String getProblemTitle() { return "문제1"; }
            public Long getRequesterId() { return 1L; }
            public String getRequesterNickname() { return "사용자1"; }
            public Language getLanguage() { return Language.JAVA; }
            public boolean isClosed() { return false; }
            public LocalDateTime getCreatedAt() { return LocalDateTime.of(2026, 11, 4, 0, 0); }
        };
        given(reviewRequestRepository.findSummaries(eq(2L), eq(1L), any()))
            .willReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1));

        // when
        ReviewRequestPageResponse response = service.list(2L, 1L, 0, 20);

        // then
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content().getFirst().requester()).isEqualTo("사용자1");
        assertThat(response.content().getFirst().problemTitle()).isEqualTo("문제1");
    }

    @Test
    void 요청자가_닫으면_요청이_닫힌다() {
        // given
        given(reviewRequestRepository.findWithRequesterById(5L)).willReturn(Optional.of(request(5L, user(1L), false)));
        given(reviewRequestRepository.close(eq(5L), any())).willReturn(1);

        // when
        service.close(1L, 5L);

        // then
        then(reviewRequestRepository).should().close(eq(5L), any());
    }

    @Test
    void 요청자가_아니면_닫을_수_없다() {
        // given
        given(reviewRequestRepository.findWithRequesterById(5L)).willReturn(Optional.of(request(5L, user(1L), false)));

        // when & then
        assertThatThrownBy(() -> service.close(2L, 5L)).isInstanceOf(RequesterOnlyException.class);
        then(reviewRequestRepository).should(never()).close(any(), any());
    }

    @Test
    void 이미_닫힌_요청을_다시_닫으면_실패한다() {
        // given
        given(reviewRequestRepository.findWithRequesterById(5L)).willReturn(Optional.of(request(5L, user(1L), false)));
        given(reviewRequestRepository.close(eq(5L), any())).willReturn(0);

        // when & then
        assertThatThrownBy(() -> service.close(1L, 5L)).isInstanceOf(ReviewRequestClosedException.class);
    }

    @Test
    void 제안_코드로_선택한_줄을_바꾼_코드를_돌려준다() {
        // given
        User requester = user(1L);
        ReviewRequest request = request(5L, requester, false);
        given(reviewRequestRepository.findWithRequesterById(5L)).willReturn(Optional.of(request));
        given(lineCommentRepository.findByIdAndReviewRequestId(7L, 5L))
            .willReturn(Optional.of(lineComment(7L, request, user(2L), 2, "int b = 20;")));

        // when
        ApplyResponse response = service.apply(1L, 5L, new ApplyRequest(List.of(7L)));

        // then
        assertThat(response.code()).isEqualTo("int a = 1;\nint b = 20;\nint c = 3;\n");
    }

    @Test
    void 닫힌_요청도_요청자는_적용할_수_있다() {
        // given
        ReviewRequest request = request(5L, user(1L), true);
        given(reviewRequestRepository.findWithRequesterById(5L)).willReturn(Optional.of(request));
        given(lineCommentRepository.findByIdAndReviewRequestId(7L, 5L))
            .willReturn(Optional.of(lineComment(7L, request, user(2L), 1, "int a = 10;")));

        // when
        ApplyResponse response = service.apply(1L, 5L, new ApplyRequest(List.of(7L)));

        // then
        assertThat(response.code()).startsWith("int a = 10;\n");
    }

    @Test
    void 요청자가_아니면_적용할_수_없다() {
        // given
        given(reviewRequestRepository.findWithRequesterById(5L)).willReturn(Optional.of(request(5L, user(1L), false)));

        // when & then
        assertThatThrownBy(() -> service.apply(2L, 5L, new ApplyRequest(List.of(7L))))
            .isInstanceOf(RequesterOnlyException.class);
    }

    @Test
    void 코멘트를_하나가_아닌_개수로_선택하면_적용할_수_없다() {
        // given
        given(reviewRequestRepository.findWithRequesterById(5L)).willReturn(Optional.of(request(5L, user(1L), false)));

        // when & then
        assertThatThrownBy(() -> service.apply(1L, 5L, new ApplyRequest(List.of())))
            .isInstanceOf(InvalidApplySelectionException.class);
        assertThatThrownBy(() -> service.apply(1L, 5L, new ApplyRequest(List.of(7L, 8L))))
            .isInstanceOf(InvalidApplySelectionException.class);
    }

    @Test
    void 같은_코멘트를_두_번_선택해도_하나가_아니므로_적용할_수_없다() {
        // given
        given(reviewRequestRepository.findWithRequesterById(5L)).willReturn(Optional.of(request(5L, user(1L), false)));

        // when & then
        assertThatThrownBy(() -> service.apply(1L, 5L, new ApplyRequest(List.of(7L, 7L))))
            .isInstanceOf(InvalidApplySelectionException.class);
    }

    @Test
    void 다른_요청의_코멘트면_적용할_수_없다() {
        // given
        given(reviewRequestRepository.findWithRequesterById(5L)).willReturn(Optional.of(request(5L, user(1L), false)));
        given(lineCommentRepository.findByIdAndReviewRequestId(7L, 5L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.apply(1L, 5L, new ApplyRequest(List.of(7L))))
            .isInstanceOf(ReviewCommentNotFoundException.class);
    }

    @Test
    void 제안_코드가_없는_코멘트면_적용할_수_없다() {
        // given
        ReviewRequest request = request(5L, user(1L), false);
        given(reviewRequestRepository.findWithRequesterById(5L)).willReturn(Optional.of(request));
        given(lineCommentRepository.findByIdAndReviewRequestId(7L, 5L))
            .willReturn(Optional.of(lineComment(7L, request, user(2L), 2, null)));

        // when & then
        assertThatThrownBy(() -> service.apply(1L, 5L, new ApplyRequest(List.of(7L))))
            .isInstanceOf(SuggestionMissingException.class);
    }

    private DataIntegrityViolationException integrityError(ConstraintViolationException.ConstraintKind kind,
                                                           String constraintName) {
        return new DataIntegrityViolationException("무결성 오류",
            new ConstraintViolationException("무결성 오류", new SQLException(), kind, constraintName));
    }
}

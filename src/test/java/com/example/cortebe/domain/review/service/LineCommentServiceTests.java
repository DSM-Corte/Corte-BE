package com.example.cortebe.domain.review.service;

import com.example.cortebe.domain.review.dto.CreateLineCommentRequest;
import com.example.cortebe.domain.review.dto.LineCommentResponse;
import com.example.cortebe.domain.review.dto.UpdateLineCommentRequest;
import com.example.cortebe.domain.review.entity.LineComment;
import com.example.cortebe.domain.review.entity.ReviewRequest;
import com.example.cortebe.domain.review.exception.InvalidLineNumberException;
import com.example.cortebe.domain.review.exception.LineCommentLimitExceededException;
import com.example.cortebe.domain.review.exception.RequesterCannotCommentException;
import com.example.cortebe.domain.review.exception.ReviewCommentForbiddenException;
import com.example.cortebe.domain.review.exception.ReviewCommentNotFoundException;
import com.example.cortebe.domain.review.exception.ReviewRequestClosedException;
import com.example.cortebe.domain.review.exception.ReviewRequestNotFoundException;
import com.example.cortebe.domain.review.repository.LineCommentRepository;
import com.example.cortebe.domain.review.repository.ReviewReplyRepository;
import com.example.cortebe.domain.review.repository.ReviewRequestRepository;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.security.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.example.cortebe.domain.review.ReviewFixtures.lineComment;
import static com.example.cortebe.domain.review.ReviewFixtures.reply;
import static com.example.cortebe.domain.review.ReviewFixtures.request;
import static com.example.cortebe.domain.review.ReviewFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class LineCommentServiceTests {

    @Mock
    private LineCommentRepository lineCommentRepository;

    @Mock
    private ReviewReplyRepository reviewReplyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReviewRequestRepository reviewRequestRepository;

    private LineCommentService service;

    @BeforeEach
    void setUp() {
        service = new LineCommentService(lineCommentRepository, reviewReplyRepository, userRepository,
            new ReviewRequestFinder(reviewRequestRepository));
    }

    @Test
    void 리뷰어가_줄_코멘트를_만든다() {
        // given
        givenRequest(request(5L, user(1L), false));
        given(userRepository.findById(2L)).willReturn(Optional.of(user(2L)));
        given(lineCommentRepository.countByReviewRequestId(5L)).willReturn(0L);
        given(lineCommentRepository.save(any(LineComment.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        LineCommentResponse response = service.create(2L, 5L, new CreateLineCommentRequest(2, "```java\nint x;\n```", "int b = 20;"));

        // then
        assertThat(response.lineNumber()).isEqualTo(2);
        assertThat(response.content()).isEqualTo("```java\nint x;\n```");
        assertThat(response.suggestedCode()).isEqualTo("int b = 20;");
        assertThat(response.authorId()).isEqualTo(2L);
        assertThat(response.replies()).isEmpty();
    }

    @Test
    void 요청자는_줄_코멘트를_만들_수_없다() {
        // given
        givenRequest(request(5L, user(1L), false));

        // when & then
        assertThatThrownBy(() -> service.create(1L, 5L, new CreateLineCommentRequest(1, "내용", null)))
            .isInstanceOf(RequesterCannotCommentException.class);
        then(lineCommentRepository).should(never()).save(any());
    }

    @Test
    void 닫힌_요청이면_요청자는_역할_오류를_리뷰어는_없음_오류를_받는다() {
        // given
        givenRequest(request(5L, user(1L), true));

        // when & then
        assertThatThrownBy(() -> service.create(1L, 5L, new CreateLineCommentRequest(1, "내용", null)))
            .isInstanceOf(RequesterCannotCommentException.class);
        assertThatThrownBy(() -> service.create(2L, 5L, new CreateLineCommentRequest(1, "내용", null)))
            .isInstanceOf(ReviewRequestNotFoundException.class);
    }

    @Test
    void 코드_줄_수를_넘는_줄_번호면_코멘트를_만들_수_없다() {
        // given
        givenRequest(request(5L, user(1L), false));
        given(userRepository.findById(2L)).willReturn(Optional.of(user(2L)));

        // when & then
        assertThatThrownBy(() -> service.create(2L, 5L, new CreateLineCommentRequest(4, "내용", null)))
            .isInstanceOf(InvalidLineNumberException.class);
    }

    @Test
    void 마지막_줄_번호까지는_코멘트를_만들_수_있다() {
        // given
        givenRequest(request(5L, user(1L), false));
        given(userRepository.findById(2L)).willReturn(Optional.of(user(2L)));
        given(lineCommentRepository.countByReviewRequestId(5L)).willReturn(0L);
        given(lineCommentRepository.save(any(LineComment.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        LineCommentResponse response = service.create(2L, 5L, new CreateLineCommentRequest(3, "내용", null));

        // then
        assertThat(response.lineNumber()).isEqualTo(3);
    }

    @Test
    void 요청당_줄_코멘트가_200개이면_더_만들_수_없다() {
        // given
        givenRequest(request(5L, user(1L), false));
        given(userRepository.findById(2L)).willReturn(Optional.of(user(2L)));
        given(lineCommentRepository.countByReviewRequestId(5L)).willReturn(200L);

        // when & then
        assertThatThrownBy(() -> service.create(2L, 5L, new CreateLineCommentRequest(1, "내용", null)))
            .isInstanceOf(LineCommentLimitExceededException.class);
        then(lineCommentRepository).should(never()).save(any());
    }

    @Test
    void 회원이_없으면_줄_코멘트를_만들_수_없다() {
        // given
        givenRequest(request(5L, user(1L), false));
        given(userRepository.findById(2L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.create(2L, 5L, new CreateLineCommentRequest(1, "내용", null)))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void 목록은_답글을_코멘트별로_묶어_돌려준다() {
        // given
        ReviewRequest request = request(5L, user(1L), false);
        LineComment first = lineComment(7L, request, user(2L), 1, null);
        LineComment second = lineComment(8L, request, user(3L), 2, "x");
        givenRequest(request);
        given(lineCommentRepository.findByReviewRequestIdOrderByLineNumberAscIdAsc(5L)).willReturn(List.of(first, second));
        given(reviewReplyRepository.findByLineCommentIdInOrderByIdAsc(List.of(7L, 8L)))
            .willReturn(List.of(reply(20L, request, second, null, user(1L))));

        // when
        List<LineCommentResponse> responses = service.list(1L, 5L);

        // then
        assertThat(responses).extracting(LineCommentResponse::id).containsExactly(7L, 8L);
        assertThat(responses.get(0).replies()).isEmpty();
        assertThat(responses.get(1).replies()).extracting(reply -> reply.id()).containsExactly(20L);
    }

    @Test
    void 코멘트가_없으면_답글을_조회하지_않는다() {
        // given
        givenRequest(request(5L, user(1L), false));
        given(lineCommentRepository.findByReviewRequestIdOrderByLineNumberAscIdAsc(5L)).willReturn(List.of());

        // when
        List<LineCommentResponse> responses = service.list(1L, 5L);

        // then
        assertThat(responses).isEmpty();
        then(reviewReplyRepository).shouldHaveNoInteractions();
    }

    @Test
    void 쓴_사람이_수정하면_바뀐_내용을_돌려준다() {
        // given
        ReviewRequest request = request(5L, user(1L), false);
        givenRequest(request);
        given(lineCommentRepository.findWithAuthorByIdAndReviewRequestId(7L, 5L))
            .willReturn(Optional.of(lineComment(7L, request, user(2L), 1, "원래")));
        given(reviewReplyRepository.findByLineCommentIdInOrderByIdAsc(List.of(7L))).willReturn(List.of());

        // when
        LineCommentResponse response = service.update(2L, 5L, 7L, new UpdateLineCommentRequest("새 내용", null));

        // then
        assertThat(response.content()).isEqualTo("새 내용");
        assertThat(response.suggestedCode()).isEqualTo("원래");
    }

    @Test
    void 쓴_사람이_아니면_수정할_수_없다() {
        // given
        ReviewRequest request = request(5L, user(1L), false);
        givenRequest(request);
        given(lineCommentRepository.findWithAuthorByIdAndReviewRequestId(7L, 5L))
            .willReturn(Optional.of(lineComment(7L, request, user(2L), 1, null)));

        // when & then
        assertThatThrownBy(() -> service.update(3L, 5L, 7L, new UpdateLineCommentRequest("새 내용", null)))
            .isInstanceOf(ReviewCommentForbiddenException.class);
    }

    @Test
    void 닫힌_요청의_코멘트는_요청자도_수정할_수_없다() {
        // given
        ReviewRequest request = request(5L, user(1L), true);
        givenRequest(request);
        given(lineCommentRepository.findWithAuthorByIdAndReviewRequestId(7L, 5L))
            .willReturn(Optional.of(lineComment(7L, request, user(1L), 1, null)));

        // when & then
        assertThatThrownBy(() -> service.update(1L, 5L, 7L, new UpdateLineCommentRequest("새 내용", null)))
            .isInstanceOf(ReviewRequestClosedException.class);
    }

    @Test
    void 닫힌_요청이어도_쓴_사람이_아니면_닫힘_오류보다_권한_오류가_먼저다() {
        // given
        ReviewRequest request = request(5L, user(1L), true);
        givenRequest(request);
        given(lineCommentRepository.findWithAuthorByIdAndReviewRequestId(7L, 5L))
            .willReturn(Optional.of(lineComment(7L, request, user(2L), 1, null)));

        // when & then
        assertThatThrownBy(() -> service.update(1L, 5L, 7L, new UpdateLineCommentRequest("새 내용", null)))
            .isInstanceOf(ReviewCommentForbiddenException.class);
    }

    @Test
    void 없는_코멘트는_수정할_수_없다() {
        // given
        givenRequest(request(5L, user(1L), false));
        given(lineCommentRepository.findWithAuthorByIdAndReviewRequestId(7L, 5L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.update(2L, 5L, 7L, new UpdateLineCommentRequest("새 내용", null)))
            .isInstanceOf(ReviewCommentNotFoundException.class);
    }

    @Test
    void 쓴_사람이_삭제하면_답글을_먼저_지우고_코멘트를_지운다() {
        // given
        ReviewRequest request = request(5L, user(1L), false);
        LineComment comment = lineComment(7L, request, user(2L), 1, null);
        givenRequest(request);
        given(lineCommentRepository.findWithAuthorByIdAndReviewRequestId(7L, 5L)).willReturn(Optional.of(comment));

        // when
        service.delete(2L, 5L, 7L);

        // then
        InOrder order = inOrder(reviewReplyRepository, lineCommentRepository);
        order.verify(reviewReplyRepository).deleteAllByLineCommentId(7L);
        order.verify(lineCommentRepository).delete(comment);
    }

    @Test
    void 쓴_사람이_아니면_삭제할_수_없다() {
        // given
        ReviewRequest request = request(5L, user(1L), false);
        givenRequest(request);
        given(lineCommentRepository.findWithAuthorByIdAndReviewRequestId(7L, 5L))
            .willReturn(Optional.of(lineComment(7L, request, user(2L), 1, null)));

        // when & then
        assertThatThrownBy(() -> service.delete(3L, 5L, 7L)).isInstanceOf(ReviewCommentForbiddenException.class);
        then(lineCommentRepository).should(never()).delete(any());
    }

    private void givenRequest(ReviewRequest request) {
        given(reviewRequestRepository.findWithRequesterById(5L)).willReturn(Optional.of(request));
    }
}

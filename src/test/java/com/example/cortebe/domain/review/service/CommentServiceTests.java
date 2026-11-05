package com.example.cortebe.domain.review.service;

import com.example.cortebe.domain.review.dto.CommentPageResponse;
import com.example.cortebe.domain.review.dto.CommentResponse;
import com.example.cortebe.domain.review.dto.CreateCommentRequest;
import com.example.cortebe.domain.review.dto.UpdateCommentRequest;
import com.example.cortebe.domain.review.entity.ReviewComment;
import com.example.cortebe.domain.review.entity.ReviewRequest;
import com.example.cortebe.domain.review.exception.RequesterCannotCommentException;
import com.example.cortebe.domain.review.exception.ReviewCommentForbiddenException;
import com.example.cortebe.domain.review.exception.ReviewCommentNotFoundException;
import com.example.cortebe.domain.review.exception.ReviewRequestClosedException;
import com.example.cortebe.domain.review.repository.ReviewCommentRepository;
import com.example.cortebe.domain.review.repository.ReviewReplyRepository;
import com.example.cortebe.domain.review.repository.ReviewRequestRepository;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.error.exception.InvalidInputException;
import com.example.cortebe.global.security.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static com.example.cortebe.domain.review.ReviewFixtures.comment;
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
class CommentServiceTests {

    @Mock
    private ReviewCommentRepository reviewCommentRepository;

    @Mock
    private ReviewReplyRepository reviewReplyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReviewRequestRepository reviewRequestRepository;

    private CommentService service;

    @BeforeEach
    void setUp() {
        service = new CommentService(reviewCommentRepository, reviewReplyRepository, userRepository,
            new ReviewRequestFinder(reviewRequestRepository));
    }

    @Test
    void 리뷰어가_전체_댓글을_만든다() {
        // given
        givenRequest(request(5L, user(1L), false));
        given(userRepository.findById(2L)).willReturn(Optional.of(user(2L)));
        given(reviewCommentRepository.save(any(ReviewComment.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        CommentResponse response = service.create(2L, 5L, new CreateCommentRequest("전체적으로 좋아요"));

        // then
        assertThat(response.content()).isEqualTo("전체적으로 좋아요");
        assertThat(response.authorId()).isEqualTo(2L);
        assertThat(response.replies()).isEmpty();
    }

    @Test
    void 요청자는_전체_댓글을_만들_수_없다() {
        // given
        givenRequest(request(5L, user(1L), false));

        // when & then
        assertThatThrownBy(() -> service.create(1L, 5L, new CreateCommentRequest("내용")))
            .isInstanceOf(RequesterCannotCommentException.class);
        then(reviewCommentRepository).should(never()).save(any());
    }

    @Test
    void 회원이_없으면_전체_댓글을_만들_수_없다() {
        // given
        givenRequest(request(5L, user(1L), false));
        given(userRepository.findById(2L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.create(2L, 5L, new CreateCommentRequest("내용")))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void 페이지_범위를_벗어나면_댓글_목록_조회에_실패한다() {
        // when & then
        assertThatThrownBy(() -> service.list(1L, 5L, 0, 101)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> service.list(1L, 5L, -1, 20)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void 댓글_목록은_답글을_묶어_페이지로_돌려준다() {
        // given
        ReviewRequest request = request(5L, user(1L), false);
        ReviewComment first = comment(7L, request, user(2L));
        givenRequest(request);
        given(reviewCommentRepository.findByReviewRequestId(any(Long.class), any()))
            .willReturn(new PageImpl<>(List.of(first), PageRequest.of(0, 20), 1));
        given(reviewReplyRepository.findByReviewCommentIdInOrderByIdAsc(List.of(7L)))
            .willReturn(List.of(reply(20L, request, null, first, user(1L))));

        // when
        CommentPageResponse response = service.list(1L, 5L, 0, 20);

        // then
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content().getFirst().replies()).extracting(reply -> reply.id()).containsExactly(20L);
    }

    @Test
    void 쓴_사람이_수정하면_바뀐_내용을_돌려준다() {
        // given
        ReviewRequest request = request(5L, user(1L), false);
        givenRequest(request);
        given(reviewCommentRepository.findWithAuthorByIdAndReviewRequestId(7L, 5L))
            .willReturn(Optional.of(comment(7L, request, user(2L))));
        given(reviewReplyRepository.findByReviewCommentIdInOrderByIdAsc(List.of(7L))).willReturn(List.of());

        // when
        CommentResponse response = service.update(2L, 5L, 7L, new UpdateCommentRequest("새 내용"));

        // then
        assertThat(response.content()).isEqualTo("새 내용");
    }

    @Test
    void 쓴_사람이_아니면_댓글을_수정할_수_없다() {
        // given
        ReviewRequest request = request(5L, user(1L), false);
        givenRequest(request);
        given(reviewCommentRepository.findWithAuthorByIdAndReviewRequestId(7L, 5L))
            .willReturn(Optional.of(comment(7L, request, user(2L))));

        // when & then
        assertThatThrownBy(() -> service.update(3L, 5L, 7L, new UpdateCommentRequest("새 내용")))
            .isInstanceOf(ReviewCommentForbiddenException.class);
    }

    @Test
    void 없는_댓글은_수정할_수_없다() {
        // given
        givenRequest(request(5L, user(1L), false));
        given(reviewCommentRepository.findWithAuthorByIdAndReviewRequestId(7L, 5L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.update(2L, 5L, 7L, new UpdateCommentRequest("새 내용")))
            .isInstanceOf(ReviewCommentNotFoundException.class);
    }

    @Test
    void 닫힌_요청의_댓글은_삭제할_수_없다() {
        // given
        ReviewRequest request = request(5L, user(1L), true);
        givenRequest(request);
        given(reviewCommentRepository.findWithAuthorByIdAndReviewRequestId(7L, 5L))
            .willReturn(Optional.of(comment(7L, request, user(1L))));

        // when & then
        assertThatThrownBy(() -> service.delete(1L, 5L, 7L)).isInstanceOf(ReviewRequestClosedException.class);
        then(reviewCommentRepository).should(never()).delete(any());
    }

    @Test
    void 쓴_사람이_삭제하면_답글을_먼저_지우고_댓글을_지운다() {
        // given
        ReviewRequest request = request(5L, user(1L), false);
        ReviewComment comment = comment(7L, request, user(2L));
        givenRequest(request);
        given(reviewCommentRepository.findWithAuthorByIdAndReviewRequestId(7L, 5L)).willReturn(Optional.of(comment));

        // when
        service.delete(2L, 5L, 7L);

        // then
        InOrder order = inOrder(reviewReplyRepository, reviewCommentRepository);
        order.verify(reviewReplyRepository).deleteAllByReviewCommentId(7L);
        order.verify(reviewCommentRepository).delete(comment);
    }

    @Test
    void 쓴_사람이_아니면_댓글을_삭제할_수_없다() {
        // given
        ReviewRequest request = request(5L, user(1L), false);
        givenRequest(request);
        given(reviewCommentRepository.findWithAuthorByIdAndReviewRequestId(7L, 5L))
            .willReturn(Optional.of(comment(7L, request, user(2L))));

        // when & then
        assertThatThrownBy(() -> service.delete(3L, 5L, 7L)).isInstanceOf(ReviewCommentForbiddenException.class);
        then(reviewCommentRepository).should(never()).delete(any());
    }

    private void givenRequest(ReviewRequest request) {
        given(reviewRequestRepository.findWithRequesterById(5L)).willReturn(Optional.of(request));
    }
}

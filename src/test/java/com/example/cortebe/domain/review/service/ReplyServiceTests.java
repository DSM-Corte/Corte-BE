package com.example.cortebe.domain.review.service;

import com.example.cortebe.domain.review.dto.CreateReplyRequest;
import com.example.cortebe.domain.review.dto.ReplyResponse;
import com.example.cortebe.domain.review.dto.UpdateReplyRequest;
import com.example.cortebe.domain.review.entity.ReviewReply;
import com.example.cortebe.domain.review.entity.ReviewRequest;
import com.example.cortebe.domain.review.exception.ReviewCommentForbiddenException;
import com.example.cortebe.domain.review.exception.ReviewCommentNotFoundException;
import com.example.cortebe.domain.review.exception.ReviewRequestClosedException;
import com.example.cortebe.domain.review.exception.ReviewRequestNotFoundException;
import com.example.cortebe.domain.review.repository.LineCommentRepository;
import com.example.cortebe.domain.review.repository.ReviewCommentRepository;
import com.example.cortebe.domain.review.repository.ReviewReplyRepository;
import com.example.cortebe.domain.review.repository.ReviewRequestRepository;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.security.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.example.cortebe.domain.review.ReviewFixtures.comment;
import static com.example.cortebe.domain.review.ReviewFixtures.lineComment;
import static com.example.cortebe.domain.review.ReviewFixtures.reply;
import static com.example.cortebe.domain.review.ReviewFixtures.request;
import static com.example.cortebe.domain.review.ReviewFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ReplyServiceTests {

    @Mock
    private ReviewReplyRepository reviewReplyRepository;

    @Mock
    private LineCommentRepository lineCommentRepository;

    @Mock
    private ReviewCommentRepository reviewCommentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReviewRequestRepository reviewRequestRepository;

    private ReplyService service;

    @BeforeEach
    void setUp() {
        service = new ReplyService(reviewReplyRepository, lineCommentRepository, reviewCommentRepository,
            userRepository, new ReviewRequestFinder(reviewRequestRepository));
    }

    @Test
    void 요청자도_줄_코멘트에_답글을_만든다() {
        // given
        ReviewRequest request = request(5L, user(1L), false);
        givenRequest(request);
        given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
        given(lineCommentRepository.findByIdAndReviewRequestId(7L, 5L))
            .willReturn(Optional.of(lineComment(7L, request, user(2L), 1, null)));
        given(reviewReplyRepository.save(any(ReviewReply.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        ReplyResponse response = service.createOnLineComment(1L, 5L, 7L, new CreateReplyRequest("고마워요"));

        // then
        assertThat(response.content()).isEqualTo("고마워요");
        assertThat(response.authorId()).isEqualTo(1L);
        ArgumentCaptor<ReviewReply> saved = ArgumentCaptor.forClass(ReviewReply.class);
        then(reviewReplyRepository).should().save(saved.capture());
        assertThat(saved.getValue().getLineComment().getId()).isEqualTo(7L);
        assertThat(saved.getValue().getReviewComment()).isNull();
    }

    @Test
    void 리뷰어도_전체_댓글에_답글을_만든다() {
        // given
        ReviewRequest request = request(5L, user(1L), false);
        givenRequest(request);
        given(userRepository.findById(3L)).willReturn(Optional.of(user(3L)));
        given(reviewCommentRepository.findWithAuthorByIdAndReviewRequestId(7L, 5L))
            .willReturn(Optional.of(comment(7L, request, user(2L))));
        given(reviewReplyRepository.save(any(ReviewReply.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        ReplyResponse response = service.createOnComment(3L, 5L, 7L, new CreateReplyRequest("저도 동의해요"));

        // then
        assertThat(response.authorId()).isEqualTo(3L);
        ArgumentCaptor<ReviewReply> saved = ArgumentCaptor.forClass(ReviewReply.class);
        then(reviewReplyRepository).should().save(saved.capture());
        assertThat(saved.getValue().getReviewComment().getId()).isEqualTo(7L);
        assertThat(saved.getValue().getLineComment()).isNull();
    }

    @Test
    void 닫힌_요청에는_요청자도_답글을_만들_수_없다() {
        // given
        givenRequest(request(5L, user(1L), true));

        // when & then
        assertThatThrownBy(() -> service.createOnLineComment(1L, 5L, 7L, new CreateReplyRequest("내용")))
            .isInstanceOf(ReviewRequestClosedException.class);
        then(reviewReplyRepository).should(never()).save(any());
    }

    @Test
    void 닫힌_요청에는_리뷰어가_답글을_만들_수_없다() {
        // given
        givenRequest(request(5L, user(1L), true));

        // when & then
        assertThatThrownBy(() -> service.createOnComment(2L, 5L, 7L, new CreateReplyRequest("내용")))
            .isInstanceOf(ReviewRequestNotFoundException.class);
    }

    @Test
    void 없는_줄_코멘트에는_답글을_만들_수_없다() {
        // given
        givenRequest(request(5L, user(1L), false));
        given(lineCommentRepository.findByIdAndReviewRequestId(7L, 5L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.createOnLineComment(2L, 5L, 7L, new CreateReplyRequest("내용")))
            .isInstanceOf(ReviewCommentNotFoundException.class);
    }

    @Test
    void 없는_전체_댓글에는_답글을_만들_수_없다() {
        // given
        givenRequest(request(5L, user(1L), false));
        given(reviewCommentRepository.findWithAuthorByIdAndReviewRequestId(7L, 5L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.createOnComment(2L, 5L, 7L, new CreateReplyRequest("내용")))
            .isInstanceOf(ReviewCommentNotFoundException.class);
    }

    @Test
    void 회원이_없으면_답글을_만들_수_없다() {
        // given
        ReviewRequest request = request(5L, user(1L), false);
        givenRequest(request);
        given(lineCommentRepository.findByIdAndReviewRequestId(7L, 5L))
            .willReturn(Optional.of(lineComment(7L, request, user(2L), 1, null)));
        given(userRepository.findById(2L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.createOnLineComment(2L, 5L, 7L, new CreateReplyRequest("내용")))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void 쓴_사람이_답글을_수정한다() {
        // given
        ReviewRequest request = request(5L, user(1L), false);
        givenRequest(request);
        given(reviewReplyRepository.findWithAuthorByIdAndReviewRequestId(9L, 5L))
            .willReturn(Optional.of(reply(9L, request, null, null, user(2L))));

        // when
        ReplyResponse response = service.update(2L, 5L, 9L, new UpdateReplyRequest("새 내용"));

        // then
        assertThat(response.content()).isEqualTo("새 내용");
    }

    @Test
    void 쓴_사람이_아니면_답글을_수정할_수_없다() {
        // given
        ReviewRequest request = request(5L, user(1L), false);
        givenRequest(request);
        given(reviewReplyRepository.findWithAuthorByIdAndReviewRequestId(9L, 5L))
            .willReturn(Optional.of(reply(9L, request, null, null, user(2L))));

        // when & then
        assertThatThrownBy(() -> service.update(3L, 5L, 9L, new UpdateReplyRequest("새 내용")))
            .isInstanceOf(ReviewCommentForbiddenException.class);
    }

    @Test
    void 없는_답글은_수정할_수_없다() {
        // given
        givenRequest(request(5L, user(1L), false));
        given(reviewReplyRepository.findWithAuthorByIdAndReviewRequestId(9L, 5L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.update(2L, 5L, 9L, new UpdateReplyRequest("새 내용")))
            .isInstanceOf(ReviewCommentNotFoundException.class);
    }

    @Test
    void 닫힌_요청의_답글은_쓴_사람도_수정하거나_삭제할_수_없다() {
        // given
        ReviewRequest request = request(5L, user(1L), true);
        givenRequest(request);
        given(reviewReplyRepository.findWithAuthorByIdAndReviewRequestId(9L, 5L))
            .willReturn(Optional.of(reply(9L, request, null, null, user(1L))));

        // when & then
        assertThatThrownBy(() -> service.update(1L, 5L, 9L, new UpdateReplyRequest("새 내용")))
            .isInstanceOf(ReviewRequestClosedException.class);
        assertThatThrownBy(() -> service.delete(1L, 5L, 9L)).isInstanceOf(ReviewRequestClosedException.class);
        then(reviewReplyRepository).should(never()).delete(any());
    }

    @Test
    void 닫힌_요청이어도_쓴_사람이_아니면_닫힘_오류보다_권한_오류가_먼저다() {
        // given
        ReviewRequest request = request(5L, user(1L), true);
        givenRequest(request);
        given(reviewReplyRepository.findWithAuthorByIdAndReviewRequestId(9L, 5L))
            .willReturn(Optional.of(reply(9L, request, null, null, user(2L))));

        // when & then
        assertThatThrownBy(() -> service.delete(1L, 5L, 9L)).isInstanceOf(ReviewCommentForbiddenException.class);
    }

    @Test
    void 쓴_사람이_답글을_삭제한다() {
        // given
        ReviewRequest request = request(5L, user(1L), false);
        ReviewReply reply = reply(9L, request, null, null, user(2L));
        givenRequest(request);
        given(reviewReplyRepository.findWithAuthorByIdAndReviewRequestId(9L, 5L)).willReturn(Optional.of(reply));

        // when
        service.delete(2L, 5L, 9L);

        // then
        then(reviewReplyRepository).should().delete(reply);
    }

    @Test
    void 쓴_사람이_아니면_답글을_삭제할_수_없다() {
        // given
        ReviewRequest request = request(5L, user(1L), false);
        givenRequest(request);
        given(reviewReplyRepository.findWithAuthorByIdAndReviewRequestId(9L, 5L))
            .willReturn(Optional.of(reply(9L, request, null, null, user(2L))));

        // when & then
        assertThatThrownBy(() -> service.delete(3L, 5L, 9L)).isInstanceOf(ReviewCommentForbiddenException.class);
        then(reviewReplyRepository).should(never()).delete(any());
    }

    private void givenRequest(ReviewRequest request) {
        given(reviewRequestRepository.findWithRequesterById(5L)).willReturn(Optional.of(request));
    }
}

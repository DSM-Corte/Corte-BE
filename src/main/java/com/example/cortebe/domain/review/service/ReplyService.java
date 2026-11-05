package com.example.cortebe.domain.review.service;

import com.example.cortebe.domain.review.dto.CreateReplyRequest;
import com.example.cortebe.domain.review.dto.ReplyResponse;
import com.example.cortebe.domain.review.dto.UpdateReplyRequest;
import com.example.cortebe.domain.review.entity.LineComment;
import com.example.cortebe.domain.review.entity.ReviewComment;
import com.example.cortebe.domain.review.entity.ReviewReply;
import com.example.cortebe.domain.review.entity.ReviewRequest;
import com.example.cortebe.domain.review.exception.ReviewCommentForbiddenException;
import com.example.cortebe.domain.review.exception.ReviewCommentNotFoundException;
import com.example.cortebe.domain.review.repository.LineCommentRepository;
import com.example.cortebe.domain.review.repository.ReviewCommentRepository;
import com.example.cortebe.domain.review.repository.ReviewReplyRepository;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.security.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReplyService {

    private final ReviewReplyRepository reviewReplyRepository;
    private final LineCommentRepository lineCommentRepository;
    private final ReviewCommentRepository reviewCommentRepository;
    private final UserRepository userRepository;
    private final ReviewRequestFinder finder;

    @Transactional
    public ReplyResponse createOnLineComment(Long userId, Long requestId, Long commentId, CreateReplyRequest request) {
        ReviewRequest reviewRequest = finder.findWritable(requestId, userId);
        LineComment parent = lineCommentRepository.findByIdAndReviewRequestId(commentId, requestId)
            .orElseThrow(ReviewCommentNotFoundException::new);
        User author = userRepository.findById(userId).orElseThrow(UnauthorizedException::new);
        return ReplyResponse.from(reviewReplyRepository.save(ReviewReply.builder()
            .reviewRequest(reviewRequest)
            .lineComment(parent)
            .author(author)
            .content(request.content())
            .build()));
    }

    @Transactional
    public ReplyResponse createOnComment(Long userId, Long requestId, Long commentId, CreateReplyRequest request) {
        ReviewRequest reviewRequest = finder.findWritable(requestId, userId);
        ReviewComment parent = reviewCommentRepository.findWithAuthorByIdAndReviewRequestId(commentId, requestId)
            .orElseThrow(ReviewCommentNotFoundException::new);
        User author = userRepository.findById(userId).orElseThrow(UnauthorizedException::new);
        return ReplyResponse.from(reviewReplyRepository.save(ReviewReply.builder()
            .reviewRequest(reviewRequest)
            .reviewComment(parent)
            .author(author)
            .content(request.content())
            .build()));
    }

    @Transactional
    public ReplyResponse update(Long userId, Long requestId, Long replyId, UpdateReplyRequest request) {
        ReviewRequest reviewRequest = finder.findReadable(requestId, userId);
        ReviewReply reply = findOwned(userId, requestId, replyId);
        finder.requireOpen(reviewRequest);
        reply.update(request.content());
        // 변경 감지의 flush 이후 @LastModifiedDate가 반영되도록 즉시 반영한다
        reviewReplyRepository.flush();
        return ReplyResponse.from(reply);
    }

    @Transactional
    public void delete(Long userId, Long requestId, Long replyId) {
        ReviewRequest reviewRequest = finder.findReadable(requestId, userId);
        ReviewReply reply = findOwned(userId, requestId, replyId);
        finder.requireOpen(reviewRequest);
        reviewReplyRepository.delete(reply);
    }

    private ReviewReply findOwned(Long userId, Long requestId, Long replyId) {
        ReviewReply reply = reviewReplyRepository.findWithAuthorByIdAndReviewRequestId(replyId, requestId)
            .orElseThrow(ReviewCommentNotFoundException::new);
        if (!reply.isWrittenBy(userId)) {
            throw new ReviewCommentForbiddenException();
        }
        return reply;
    }
}

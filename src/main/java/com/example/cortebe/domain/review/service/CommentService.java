package com.example.cortebe.domain.review.service;

import com.example.cortebe.domain.review.dto.CommentPageResponse;
import com.example.cortebe.domain.review.dto.CommentResponse;
import com.example.cortebe.domain.review.dto.CreateCommentRequest;
import com.example.cortebe.domain.review.dto.ReplyResponse;
import com.example.cortebe.domain.review.dto.UpdateCommentRequest;
import com.example.cortebe.domain.review.entity.ReviewComment;
import com.example.cortebe.domain.review.entity.ReviewReply;
import com.example.cortebe.domain.review.entity.ReviewRequest;
import com.example.cortebe.domain.review.exception.ReviewCommentForbiddenException;
import com.example.cortebe.domain.review.exception.ReviewCommentNotFoundException;
import com.example.cortebe.domain.review.repository.ReviewCommentRepository;
import com.example.cortebe.domain.review.repository.ReviewReplyRepository;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.error.exception.InvalidInputException;
import com.example.cortebe.global.security.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final ReviewCommentRepository reviewCommentRepository;
    private final ReviewReplyRepository reviewReplyRepository;
    private final UserRepository userRepository;
    private final ReviewRequestFinder finder;

    @Transactional
    public CommentResponse create(Long userId, Long requestId, CreateCommentRequest request) {
        ReviewRequest reviewRequest = finder.findWritableByReviewer(requestId, userId);
        User author = userRepository.findById(userId).orElseThrow(UnauthorizedException::new);
        ReviewComment comment = reviewCommentRepository.save(ReviewComment.builder()
            .reviewRequest(reviewRequest)
            .author(author)
            .content(request.content())
            .build());
        return CommentResponse.from(comment, List.of());
    }

    public CommentPageResponse list(Long userId, Long requestId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidInputException();
        }
        finder.findReadable(requestId, userId);
        Page<ReviewComment> comments = reviewCommentRepository.findByReviewRequestId(requestId,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, List<ReplyResponse>> replies = comments.isEmpty() ? Map.of() : repliesOf(comments.getContent());
        return new CommentPageResponse(comments.getContent().stream()
            .map(comment -> CommentResponse.from(comment, replies.getOrDefault(comment.getId(), List.of()))).toList(),
            comments.getNumber(), comments.getSize(), comments.getTotalElements(), comments.getTotalPages());
    }

    @Transactional
    public CommentResponse update(Long userId, Long requestId, Long commentId, UpdateCommentRequest request) {
        ReviewRequest reviewRequest = finder.findReadable(requestId, userId);
        ReviewComment comment = findOwned(userId, requestId, commentId);
        finder.requireOpen(reviewRequest);
        comment.update(request.content());
        // 변경 감지의 flush 이후 @LastModifiedDate가 반영되도록 즉시 반영한다
        reviewCommentRepository.flush();
        return CommentResponse.from(comment, repliesOf(List.of(comment)).getOrDefault(commentId, List.of()));
    }

    @Transactional
    public void delete(Long userId, Long requestId, Long commentId) {
        ReviewRequest reviewRequest = finder.findReadable(requestId, userId);
        ReviewComment comment = findOwned(userId, requestId, commentId);
        finder.requireOpen(reviewRequest);
        reviewReplyRepository.deleteAllByReviewCommentId(commentId);
        reviewCommentRepository.delete(comment);
    }

    private ReviewComment findOwned(Long userId, Long requestId, Long commentId) {
        ReviewComment comment = reviewCommentRepository.findWithAuthorByIdAndReviewRequestId(commentId, requestId)
            .orElseThrow(ReviewCommentNotFoundException::new);
        if (!comment.isWrittenBy(userId)) {
            throw new ReviewCommentForbiddenException();
        }
        return comment;
    }

    private Map<Long, List<ReplyResponse>> repliesOf(List<ReviewComment> comments) {
        List<ReviewReply> replies = reviewReplyRepository
            .findByReviewCommentIdInOrderByIdAsc(comments.stream().map(ReviewComment::getId).toList());
        return replies.stream().collect(Collectors.groupingBy(reply -> reply.getReviewComment().getId(),
            Collectors.mapping(ReplyResponse::from, Collectors.toList())));
    }
}

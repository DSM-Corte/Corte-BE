package com.example.cortebe.domain.review.service;

import com.example.cortebe.domain.review.dto.CreateLineCommentRequest;
import com.example.cortebe.domain.review.dto.LineCommentResponse;
import com.example.cortebe.domain.review.dto.ReplyResponse;
import com.example.cortebe.domain.review.dto.UpdateLineCommentRequest;
import com.example.cortebe.domain.review.entity.LineComment;
import com.example.cortebe.domain.review.entity.ReviewRequest;
import com.example.cortebe.domain.review.entity.ReviewReply;
import com.example.cortebe.domain.review.exception.InvalidLineNumberException;
import com.example.cortebe.domain.review.exception.LineCommentLimitExceededException;
import com.example.cortebe.domain.review.exception.ReviewCommentForbiddenException;
import com.example.cortebe.domain.review.exception.ReviewCommentNotFoundException;
import com.example.cortebe.domain.review.repository.LineCommentRepository;
import com.example.cortebe.domain.review.repository.ReviewReplyRepository;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.security.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LineCommentService {

    static final int MAX_PER_REQUEST = 200;

    private final LineCommentRepository lineCommentRepository;
    private final ReviewReplyRepository reviewReplyRepository;
    private final UserRepository userRepository;
    private final ReviewRequestFinder finder;

    @Transactional
    public LineCommentResponse create(Long userId, Long requestId, CreateLineCommentRequest request) {
        ReviewRequest reviewRequest = finder.findWritableByReviewer(requestId, userId);
        User author = userRepository.findById(userId).orElseThrow(UnauthorizedException::new);
        if (request.lineNumber() > reviewRequest.lineCount()) {
            throw new InvalidLineNumberException();
        }
        // 동시 요청에서는 상한을 약간 넘을 수 있다(허용)
        if (lineCommentRepository.countByReviewRequestId(requestId) >= MAX_PER_REQUEST) {
            throw new LineCommentLimitExceededException();
        }
        LineComment comment = lineCommentRepository.save(LineComment.builder()
            .reviewRequest(reviewRequest)
            .author(author)
            .lineNumber(request.lineNumber())
            .content(request.content())
            .suggestedCode(request.suggestedCode())
            .build());
        return LineCommentResponse.from(comment, List.of());
    }

    public List<LineCommentResponse> list(Long userId, Long requestId) {
        finder.findReadable(requestId, userId);
        List<LineComment> comments = lineCommentRepository.findByReviewRequestIdOrderByLineNumberAscIdAsc(requestId);
        if (comments.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ReplyResponse>> replies = repliesOf(comments);
        return comments.stream()
            .map(comment -> LineCommentResponse.from(comment, replies.getOrDefault(comment.getId(), List.of())))
            .toList();
    }

    @Transactional
    public LineCommentResponse update(Long userId, Long requestId, Long commentId, UpdateLineCommentRequest request) {
        ReviewRequest reviewRequest = finder.findReadable(requestId, userId);
        LineComment comment = findOwned(userId, requestId, commentId);
        finder.requireOpen(reviewRequest);
        comment.update(request.content(), request.suggestedCode());
        // 변경 감지의 flush 이후 @LastModifiedDate가 반영되도록 즉시 반영한다
        lineCommentRepository.flush();
        return LineCommentResponse.from(comment, repliesOf(List.of(comment)).getOrDefault(commentId, List.of()));
    }

    @Transactional
    public void delete(Long userId, Long requestId, Long commentId) {
        ReviewRequest reviewRequest = finder.findReadable(requestId, userId);
        LineComment comment = findOwned(userId, requestId, commentId);
        finder.requireOpen(reviewRequest);
        reviewReplyRepository.deleteAllByLineCommentId(commentId);
        lineCommentRepository.delete(comment);
    }

    private LineComment findOwned(Long userId, Long requestId, Long commentId) {
        LineComment comment = lineCommentRepository.findWithAuthorByIdAndReviewRequestId(commentId, requestId)
            .orElseThrow(ReviewCommentNotFoundException::new);
        if (!comment.isWrittenBy(userId)) {
            throw new ReviewCommentForbiddenException();
        }
        return comment;
    }

    private Map<Long, List<ReplyResponse>> repliesOf(List<LineComment> comments) {
        List<ReviewReply> replies = reviewReplyRepository
            .findByLineCommentIdInOrderByIdAsc(comments.stream().map(LineComment::getId).toList());
        return replies.stream().collect(Collectors.groupingBy(reply -> reply.getLineComment().getId(),
            Collectors.mapping(ReplyResponse::from, Collectors.toList())));
    }
}

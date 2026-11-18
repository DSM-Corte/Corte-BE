package com.example.cortebe.domain.review.service;

import com.example.cortebe.domain.review.dto.ReplyReactionResponse;
import com.example.cortebe.domain.review.entity.ReplyReactionType;
import com.example.cortebe.domain.review.entity.ReviewReply;
import com.example.cortebe.domain.review.entity.ReviewReplyReaction;
import com.example.cortebe.domain.review.entity.ReviewRequest;
import com.example.cortebe.domain.review.exception.DuplicateReplyReactionException;
import com.example.cortebe.domain.review.exception.ReplyReactionForbiddenException;
import com.example.cortebe.domain.review.exception.ReplyReactionNotFoundException;
import com.example.cortebe.domain.review.exception.ReviewCommentNotFoundException;
import com.example.cortebe.domain.review.repository.ReviewReplyReactionRepository;
import com.example.cortebe.domain.review.repository.ReviewReplyRepository;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.security.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReplyReactionService {

    private final ReviewReplyReactionRepository repository;
    private final ReviewReplyRepository replyRepository;
    private final UserRepository userRepository;
    private final ReviewRequestFinder finder;

    @Transactional
    public ReplyReactionResponse create(Long userId, boolean admin, Long requestId, Long replyId,
                                        ReplyReactionType type) {
        // 닫기 요청의 UPDATE가 같은 요청 행을 잠그므로 닫힌 뒤 반응을 저장할 수 없다.
        ReviewRequest request = finder.findReadableForUpdate(requestId, userId);
        ReviewReply reply = findReply(replyId, requestId);
        if (!admin && !reply.isParentWrittenBy(userId)) {
            throw new ReplyReactionForbiddenException();
        }
        finder.requireOpen(request);
        User user = userRepository.findById(userId).orElseThrow(UnauthorizedException::new);
        ReviewReplyReaction reaction = ReviewReplyReaction.builder().reply(reply).author(user).type(type).build();
        try {
            return ReplyReactionResponse.from(repository.saveAndFlush(reaction));
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicate(exception)) {
                throw new DuplicateReplyReactionException();
            }
            throw exception;
        }
    }

    @Transactional
    public void delete(Long userId, boolean admin, Long requestId, Long replyId, Long reactionId) {
        ReviewRequest request = finder.findReadableForUpdate(requestId, userId);
        findReply(replyId, requestId);
        ReviewReplyReaction reaction = repository.findByIdAndReplyId(reactionId, replyId)
            .orElseThrow(ReplyReactionNotFoundException::new);
        if (!admin && !reaction.isWrittenBy(userId)) {
            throw new ReplyReactionForbiddenException();
        }
        finder.requireOpen(request);
        repository.delete(reaction);
    }

    private ReviewReply findReply(Long replyId, Long requestId) {
        return replyRepository.findForUpdateByIdAndReviewRequestId(replyId, requestId)
            .orElseThrow(ReviewCommentNotFoundException::new);
    }

    private boolean isDuplicate(DataIntegrityViolationException exception) {
        if (!(exception.getCause() instanceof ConstraintViolationException violation)
            || violation.getKind() != ConstraintViolationException.ConstraintKind.UNIQUE
            || violation.getConstraintName() == null) {
            return false;
        }
        String name = violation.getConstraintName().split(" ", 2)[0];
        name = name.substring(name.lastIndexOf('.') + 1);
        return ReviewReplyReaction.UNIQUE_REACTION.equalsIgnoreCase(name);
    }
}

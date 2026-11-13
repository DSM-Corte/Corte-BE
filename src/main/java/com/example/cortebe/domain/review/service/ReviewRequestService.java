package com.example.cortebe.domain.review.service;

import com.example.cortebe.domain.judge.entity.JudgeJob;
import com.example.cortebe.domain.judge.repository.JudgeJobRepository;
import com.example.cortebe.domain.judge.runner.Verdict;
import com.example.cortebe.domain.review.dto.ApplyRequest;
import com.example.cortebe.domain.review.dto.ApplyResponse;
import com.example.cortebe.domain.review.dto.CreateReviewRequestRequest;
import com.example.cortebe.domain.review.dto.MyReviewRequestPageResponse;
import com.example.cortebe.domain.review.dto.ReviewRequestPageResponse;
import com.example.cortebe.domain.review.dto.ReviewRequestStatusFilter;
import com.example.cortebe.domain.review.dto.ReviewRequestResponse;
import com.example.cortebe.domain.review.entity.LineComment;
import com.example.cortebe.domain.review.entity.ReviewRequest;
import com.example.cortebe.domain.review.exception.DuplicateReviewRequestException;
import com.example.cortebe.domain.review.exception.InvalidApplySelectionException;
import com.example.cortebe.domain.review.exception.RequesterOnlyException;
import com.example.cortebe.domain.review.exception.ReviewCommentNotFoundException;
import com.example.cortebe.domain.review.exception.ReviewRequestClosedException;
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
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewRequestService {

    private static final String SUBMISSION_UNIQUE_CONSTRAINT = "uk_review_requests_submission_id";

    private final ReviewRequestRepository reviewRequestRepository;
    private final JudgeJobRepository judgeJobRepository;
    private final UserRepository userRepository;
    private final LineCommentRepository lineCommentRepository;
    private final ReviewRequestFinder finder;

    @Transactional
    public ReviewRequestResponse create(Long userId, CreateReviewRequestRequest request) {
        User requester = userRepository.findById(userId).orElseThrow(UnauthorizedException::new);
        JudgeJob submission = judgeJobRepository.findWithAuthorById(request.submissionId())
            .filter(JudgeJob::isSubmission)
            .orElseThrow(SubmissionNotFoundException::new);
        if (!submission.isWrittenBy(userId)) {
            throw new SubmissionForbiddenException();
        }
        if (submission.getVerdict() != Verdict.ACCEPTED) {
            throw new SubmissionNotAcceptedException();
        }
        try {
            ReviewRequest reviewRequest = reviewRequestRepository.saveAndFlush(ReviewRequest.builder()
                .submission(submission)
                .requester(requester)
                .problem(submission.getProblem())
                .language(submission.getLanguage())
                .code(submission.getCode())
                .build());
            return ReviewRequestResponse.from(reviewRequest);
        } catch (DataIntegrityViolationException e) {
            if (isSubmissionUniqueViolation(e)) {
                throw new DuplicateReviewRequestException();
            }
            throw e;
        }
    }

    public ReviewRequestResponse get(Long userId, Long id) {
        return ReviewRequestResponse.from(finder.findReadable(id, userId));
    }

    public MyReviewRequestPageResponse listMine(Long userId, ReviewRequestStatusFilter status, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new InvalidInputException();
        }
        Boolean closed = switch (status) {
            case ALL -> null;
            case OPEN -> false;
            case CLOSED -> true;
        };
        return MyReviewRequestPageResponse.from(reviewRequestRepository.findOwnSummaries(userId, closed,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))));
    }

    public ReviewRequestPageResponse list(Long userId, Long problemId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidInputException();
        }
        Page<ReviewRequestRepository.Summary> requests = reviewRequestRepository.findSummaries(userId, problemId,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return new ReviewRequestPageResponse(requests.getContent().stream()
            .map(request -> new ReviewRequestPageResponse.Summary(request.getId(), request.getProblemId(),
                request.getProblemTitle(), request.getRequesterId(), request.getRequesterNickname(),
                request.getLanguage(), request.isClosed(), request.getCreatedAt())).toList(),
            requests.getNumber(), requests.getSize(), requests.getTotalElements(), requests.getTotalPages());
    }

    @Transactional
    public void close(Long userId, Long id) {
        ReviewRequest request = finder.findReadable(id, userId);
        requireRequester(request, userId);
        // 동시에 닫아도 한 번만 성공하도록 열려 있을 때만 갱신한다
        if (reviewRequestRepository.close(id, LocalDateTime.now()) == 0) {
            throw new ReviewRequestClosedException();
        }
    }

    public ApplyResponse apply(Long userId, Long id, ApplyRequest request) {
        ReviewRequest reviewRequest = finder.findReadable(id, userId);
        requireRequester(reviewRequest, userId);
        if (request.lineCommentIds().size() != 1) {
            throw new InvalidApplySelectionException();
        }
        LineComment comment = lineCommentRepository
            .findByIdAndReviewRequestId(request.lineCommentIds().getFirst(), id)
            .orElseThrow(ReviewCommentNotFoundException::new);
        if (comment.getSuggestedCode() == null) {
            throw new SuggestionMissingException();
        }
        return new ApplyResponse(reviewRequest.replaceLine(comment.getLineNumber(), comment.getSuggestedCode()));
    }

    private void requireRequester(ReviewRequest request, Long userId) {
        if (!request.isRequestedBy(userId)) {
            throw new RequesterOnlyException();
        }
    }

    private boolean isSubmissionUniqueViolation(DataIntegrityViolationException e) {
        if (!(e.getCause() instanceof ConstraintViolationException cause)
            || cause.getKind() != ConstraintViolationException.ConstraintKind.UNIQUE
            || cause.getConstraintName() == null) {
            return false;
        }
        // DB마다 이름 앞뒤에 다른 글자가 붙어서(H2: "PUBLIC.UK_... INDEX ...") 첫 공백 앞의 마지막 토큰만 비교한다
        String name = cause.getConstraintName().split(" ")[0];
        return name.substring(name.lastIndexOf('.') + 1).equalsIgnoreCase(SUBMISSION_UNIQUE_CONSTRAINT);
    }
}

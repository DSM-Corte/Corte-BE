package com.example.cortebe.domain.aireview.service;

import com.example.cortebe.domain.aireview.dto.AiReviewResponse;
import com.example.cortebe.domain.aireview.entity.AiReview;
import com.example.cortebe.domain.aireview.exception.AiReviewNotFoundException;
import com.example.cortebe.domain.aireview.exception.DuplicateAiReviewException;
import com.example.cortebe.domain.aireview.repository.AiReviewRepository;
import com.example.cortebe.domain.judge.entity.JudgeJob;
import com.example.cortebe.domain.judge.exception.JudgeForbiddenException;
import com.example.cortebe.domain.judge.exception.JudgeNotFoundException;
import com.example.cortebe.domain.judge.repository.JudgeJobRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiReviewStore {

    private final AiReviewRepository reviews;
    private final JudgeJobRepository jobs;
    private final ObjectMapper mapper;

    // 외부 호출 전에 소유권과 중복을 확인하고, 호출에 필요한 값만 꺼낸다
    public GeminiReviewer.Source loadSource(Long userId, Long submissionId) {
        JudgeJob job = findOwnedSubmission(userId, submissionId);
        if (reviews.existsBySubmissionId(submissionId)) {
            throw new DuplicateAiReviewException();
        }
        return new GeminiReviewer.Source(job.getProblem().getTitle(), job.getLanguage().name(),
            job.getRuntimeMs(), job.getCode());
    }

    @Transactional
    public AiReviewResponse save(Long userId, Long submissionId, GeminiReviewer.Result result) {
        JudgeJob job = findOwnedSubmission(userId, submissionId);
        try {
            AiReview review = reviews.saveAndFlush(AiReview.builder()
                .submission(job)
                .model(result.model())
                .summary(result.summary())
                .comments(mapper.writeValueAsString(result.comments()))
                .suggestion(mapper.writeValueAsString(result.suggestion()))
                .build());
            return AiReviewResponse.from(review, mapper);
        } catch (DataIntegrityViolationException e) {
            if (isSubmissionUniqueViolation(e)) {
                throw new DuplicateAiReviewException();
            }
            throw e;
        }
    }

    public AiReviewResponse get(Long userId, Long submissionId) {
        findOwnedSubmission(userId, submissionId);
        return reviews.findWithSubmissionBySubmissionId(submissionId)
            .map(review -> AiReviewResponse.from(review, mapper))
            .orElseThrow(AiReviewNotFoundException::new);
    }

    private JudgeJob findOwnedSubmission(Long userId, Long submissionId) {
        JudgeJob job = jobs.findWithAuthorById(submissionId)
            .filter(JudgeJob::isSubmission)
            .orElseThrow(JudgeNotFoundException::new);
        if (!job.isWrittenBy(userId)) {
            throw new JudgeForbiddenException();
        }
        return job;
    }

    private boolean isSubmissionUniqueViolation(DataIntegrityViolationException e) {
        if (!(e.getCause() instanceof ConstraintViolationException cause)
            || cause.getKind() != ConstraintViolationException.ConstraintKind.UNIQUE
            || cause.getConstraintName() == null) {
            return false;
        }
        // DB마다 이름 앞뒤에 다른 글자가 붙어서(H2: "PUBLIC.UK_... INDEX ...") 첫 공백 앞의 마지막 토큰만 비교한다
        String name = cause.getConstraintName().split(" ")[0];
        return name.substring(name.lastIndexOf('.') + 1)
            .equalsIgnoreCase(AiReview.SUBMISSION_UNIQUE_CONSTRAINT);
    }
}

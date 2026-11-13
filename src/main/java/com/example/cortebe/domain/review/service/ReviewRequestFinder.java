package com.example.cortebe.domain.review.service;

import com.example.cortebe.domain.review.entity.ReviewRequest;
import com.example.cortebe.domain.review.exception.RequesterCannotCommentException;
import com.example.cortebe.domain.review.exception.ReviewRequestClosedException;
import com.example.cortebe.domain.review.exception.ReviewRequestNotFoundException;
import com.example.cortebe.domain.review.repository.ReviewRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 접근(404) → 역할(403) → 닫힘(409) 순서로 검사한다
@Component
@RequiredArgsConstructor
public class ReviewRequestFinder {

    private final ReviewRequestRepository reviewRequestRepository;

    // 닫힌 요청은 요청자 외에게 존재하지 않는 것으로 보인다
    public ReviewRequest findReadable(Long id, Long userId) {
        ReviewRequest request = reviewRequestRepository.findWithRequesterById(id)
            .orElseThrow(ReviewRequestNotFoundException::new);
        if (request.isClosed() && !request.isRequestedBy(userId)) {
            throw new ReviewRequestNotFoundException();
        }
        return request;
    }

    public ReviewRequest findWritable(Long id, Long userId) {
        ReviewRequest request = findReadable(id, userId);
        requireOpen(request);
        return request;
    }

    public ReviewRequest findWritableByReviewer(Long id, Long userId) {
        ReviewRequest request = findReadable(id, userId);
        if (request.isRequestedBy(userId)) {
            throw new RequesterCannotCommentException();
        }
        requireOpen(request);
        return request;
    }

    public void requireOpen(ReviewRequest request) {
        if (request.isClosed()) {
            throw new ReviewRequestClosedException();
        }
    }
}

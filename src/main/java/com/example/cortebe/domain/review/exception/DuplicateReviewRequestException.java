package com.example.cortebe.domain.review.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class DuplicateReviewRequestException extends CorteException {

    public DuplicateReviewRequestException() {
        super(ReviewErrorCode.DUPLICATE_REVIEW_REQUEST);
    }
}

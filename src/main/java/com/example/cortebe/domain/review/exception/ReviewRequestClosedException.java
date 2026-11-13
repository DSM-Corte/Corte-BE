package com.example.cortebe.domain.review.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class ReviewRequestClosedException extends CorteException {

    public ReviewRequestClosedException() {
        super(ReviewErrorCode.REVIEW_REQUEST_CLOSED);
    }
}

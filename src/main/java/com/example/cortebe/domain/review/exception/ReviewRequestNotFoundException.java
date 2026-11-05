package com.example.cortebe.domain.review.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class ReviewRequestNotFoundException extends CorteException {

    public ReviewRequestNotFoundException() {
        super(ReviewErrorCode.REVIEW_REQUEST_NOT_FOUND);
    }
}

package com.example.cortebe.domain.review.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class SubmissionNotFoundException extends CorteException {

    public SubmissionNotFoundException() {
        super(ReviewErrorCode.SUBMISSION_NOT_FOUND);
    }
}

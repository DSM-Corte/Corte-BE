package com.example.cortebe.domain.review.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class SubmissionForbiddenException extends CorteException {

    public SubmissionForbiddenException() {
        super(ReviewErrorCode.SUBMISSION_FORBIDDEN);
    }
}

package com.example.cortebe.domain.review.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class SubmissionNotAcceptedException extends CorteException {

    public SubmissionNotAcceptedException() {
        super(ReviewErrorCode.SUBMISSION_NOT_ACCEPTED);
    }
}

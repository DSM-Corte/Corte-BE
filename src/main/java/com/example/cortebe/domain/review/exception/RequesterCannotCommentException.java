package com.example.cortebe.domain.review.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class RequesterCannotCommentException extends CorteException {

    public RequesterCannotCommentException() {
        super(ReviewErrorCode.REQUESTER_CANNOT_COMMENT);
    }
}

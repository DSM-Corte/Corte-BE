package com.example.cortebe.domain.review.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class RequesterOnlyException extends CorteException {

    public RequesterOnlyException() {
        super(ReviewErrorCode.REQUESTER_ONLY);
    }
}

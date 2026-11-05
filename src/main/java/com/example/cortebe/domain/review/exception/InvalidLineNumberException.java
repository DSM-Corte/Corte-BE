package com.example.cortebe.domain.review.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class InvalidLineNumberException extends CorteException {

    public InvalidLineNumberException() {
        super(ReviewErrorCode.INVALID_LINE_NUMBER);
    }
}

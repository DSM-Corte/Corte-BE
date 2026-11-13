package com.example.cortebe.domain.review.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class InvalidApplySelectionException extends CorteException {

    public InvalidApplySelectionException() {
        super(ReviewErrorCode.INVALID_APPLY_SELECTION);
    }
}

package com.example.cortebe.domain.review.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class SuggestionMissingException extends CorteException {

    public SuggestionMissingException() {
        super(ReviewErrorCode.SUGGESTION_MISSING);
    }
}

package com.example.cortebe.domain.aireview.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class DuplicateAiReviewException extends CorteException {
    public DuplicateAiReviewException() {
        super(AiReviewErrorCode.DUPLICATE_AI_REVIEW);
    }
}

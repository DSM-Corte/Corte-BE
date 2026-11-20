package com.example.cortebe.domain.aireview.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class AiReviewCallFailedException extends CorteException {
    public AiReviewCallFailedException() {
        super(AiReviewErrorCode.AI_REVIEW_CALL_FAILED);
    }
}

package com.example.cortebe.domain.aireview.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class AiReviewInvalidResponseException extends CorteException {
    public AiReviewInvalidResponseException() {
        super(AiReviewErrorCode.AI_REVIEW_INVALID_RESPONSE);
    }
}

package com.example.cortebe.domain.aireview.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class AiReviewNotFoundException extends CorteException {
    public AiReviewNotFoundException() {
        super(AiReviewErrorCode.AI_REVIEW_NOT_FOUND);
    }
}

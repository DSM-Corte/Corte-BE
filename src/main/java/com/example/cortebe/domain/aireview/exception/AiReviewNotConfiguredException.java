package com.example.cortebe.domain.aireview.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class AiReviewNotConfiguredException extends CorteException {
    public AiReviewNotConfiguredException() {
        super(AiReviewErrorCode.AI_REVIEW_NOT_CONFIGURED);
    }
}

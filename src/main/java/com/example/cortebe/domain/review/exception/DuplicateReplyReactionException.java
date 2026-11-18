package com.example.cortebe.domain.review.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class DuplicateReplyReactionException extends CorteException {

    public DuplicateReplyReactionException() {
        super(ReviewErrorCode.DUPLICATE_REPLY_REACTION);
    }
}

package com.example.cortebe.domain.review.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class ReplyReactionForbiddenException extends CorteException {

    public ReplyReactionForbiddenException() {
        super(ReviewErrorCode.REPLY_REACTION_FORBIDDEN);
    }
}

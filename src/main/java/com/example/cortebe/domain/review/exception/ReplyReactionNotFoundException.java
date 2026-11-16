package com.example.cortebe.domain.review.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class ReplyReactionNotFoundException extends CorteException {

    public ReplyReactionNotFoundException() {
        super(ReviewErrorCode.REPLY_REACTION_NOT_FOUND);
    }
}

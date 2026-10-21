package com.example.cortebe.domain.board.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class PostForbiddenException extends CorteException {
    public PostForbiddenException() {
        super(BoardErrorCode.POST_FORBIDDEN);
    }
}

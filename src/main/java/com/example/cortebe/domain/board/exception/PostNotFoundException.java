package com.example.cortebe.domain.board.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class PostNotFoundException extends CorteException {
    public PostNotFoundException() {
        super(BoardErrorCode.POST_NOT_FOUND);
    }
}

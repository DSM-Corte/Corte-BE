package com.example.cortebe.domain.board.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class PostCommentNotFoundException extends CorteException {
    public PostCommentNotFoundException() {
        super(BoardErrorCode.POST_COMMENT_NOT_FOUND);
    }
}

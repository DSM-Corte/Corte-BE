package com.example.cortebe.domain.board.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class PostCommentForbiddenException extends CorteException {
    public PostCommentForbiddenException() {
        super(BoardErrorCode.POST_COMMENT_FORBIDDEN);
    }
}

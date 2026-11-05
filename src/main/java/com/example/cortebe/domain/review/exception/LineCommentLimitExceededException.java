package com.example.cortebe.domain.review.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class LineCommentLimitExceededException extends CorteException {

    public LineCommentLimitExceededException() {
        super(ReviewErrorCode.LINE_COMMENT_LIMIT_EXCEEDED);
    }
}

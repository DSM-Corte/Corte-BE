package com.example.cortebe.domain.review.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class ReviewCommentForbiddenException extends CorteException {

    public ReviewCommentForbiddenException() {
        super(ReviewErrorCode.REVIEW_COMMENT_FORBIDDEN);
    }
}

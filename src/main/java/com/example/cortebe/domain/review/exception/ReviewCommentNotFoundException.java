package com.example.cortebe.domain.review.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class ReviewCommentNotFoundException extends CorteException {

    public ReviewCommentNotFoundException() {
        super(ReviewErrorCode.REVIEW_COMMENT_NOT_FOUND);
    }
}

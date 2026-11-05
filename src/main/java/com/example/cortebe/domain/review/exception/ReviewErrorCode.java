package com.example.cortebe.domain.review.exception;

import com.example.cortebe.global.error.exception.ErrorProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ReviewErrorCode implements ErrorProperty {
    REVIEW_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "리뷰 요청을 찾을 수 없습니다."),
    REVIEW_COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "R002", "코멘트나 댓글을 찾을 수 없습니다."),
    SUBMISSION_FORBIDDEN(HttpStatus.FORBIDDEN, "R003", "본인의 제출만 리뷰를 요청할 수 있습니다."),
    SUBMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "R004", "제출을 찾을 수 없습니다."),
    SUBMISSION_NOT_ACCEPTED(HttpStatus.CONFLICT, "R005", "정답인 제출만 리뷰를 요청할 수 있습니다."),
    DUPLICATE_REVIEW_REQUEST(HttpStatus.CONFLICT, "R006", "이미 리뷰를 요청한 제출입니다."),
    REQUESTER_CANNOT_COMMENT(HttpStatus.FORBIDDEN, "R007", "요청자는 코멘트와 댓글을 작성할 수 없습니다."),
    REQUESTER_ONLY(HttpStatus.FORBIDDEN, "R008", "리뷰 요청자만 사용할 수 있습니다."),
    REVIEW_COMMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "R009", "작성자만 수정하거나 삭제할 수 있습니다."),
    REVIEW_REQUEST_CLOSED(HttpStatus.CONFLICT, "R010", "닫힌 리뷰 요청입니다."),
    INVALID_LINE_NUMBER(HttpStatus.BAD_REQUEST, "R011", "줄 번호가 코드 범위를 벗어났습니다."),
    INVALID_APPLY_SELECTION(HttpStatus.BAD_REQUEST, "R012", "적용할 코멘트는 하나만 선택해야 합니다."),
    SUGGESTION_MISSING(HttpStatus.CONFLICT, "R013", "제안 코드가 없는 코멘트입니다."),
    LINE_COMMENT_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "R014", "줄 코멘트는 요청당 200개까지 작성할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

package com.example.cortebe.domain.aireview.exception;

import com.example.cortebe.global.error.exception.ErrorProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AiReviewErrorCode implements ErrorProperty {
    AI_REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "I001", "AI 리뷰를 찾을 수 없습니다."),
    DUPLICATE_AI_REVIEW(HttpStatus.CONFLICT, "I002", "이미 AI 리뷰를 생성한 제출입니다."),
    AI_REVIEW_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "I003", "AI 리뷰 기능이 설정되지 않았습니다."),
    AI_REVIEW_CALL_FAILED(HttpStatus.BAD_GATEWAY, "I004", "AI 리뷰 서버 호출에 실패했습니다."),
    AI_REVIEW_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "I005", "AI 리뷰 응답을 해석할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

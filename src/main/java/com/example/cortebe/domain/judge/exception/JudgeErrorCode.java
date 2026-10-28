package com.example.cortebe.domain.judge.exception;

import com.example.cortebe.global.error.exception.ErrorProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum JudgeErrorCode implements ErrorProperty {
    JUDGE_SUBMIT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "J001", "채점 서버에 제출하지 못했습니다."),
    JUDGE_FETCH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "J002", "채점 결과를 조회하지 못했습니다."),
    JUDGE_TIMEOUT(HttpStatus.INTERNAL_SERVER_ERROR, "J003", "채점이 진행되지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

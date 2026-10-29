package com.example.cortebe.domain.judge.exception;

import com.example.cortebe.global.error.exception.ErrorProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum JudgeErrorCode implements ErrorProperty {
    JUDGE_NOT_FOUND(HttpStatus.NOT_FOUND, "J001", "실행 또는 테스트케이스를 찾을 수 없습니다."),
    JUDGE_FORBIDDEN(HttpStatus.FORBIDDEN, "J002", "실행 결과에 접근할 권한이 없습니다."),
    JUDGE_CONFLICT(HttpStatus.CONFLICT, "J003", "현재 상태에서는 요청을 처리할 수 없습니다."),
    JUDGE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "J004", "실행 요청 제한을 초과했습니다."),
    JUDGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "J005", "실행 환경을 사용할 수 없습니다."),
    JUDGE_SUBMIT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "J006", "채점 서버에 제출하지 못했습니다."),
    JUDGE_FETCH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "J007", "채점 결과를 조회하지 못했습니다."),
    JUDGE_TIMEOUT(HttpStatus.INTERNAL_SERVER_ERROR, "J008", "채점이 진행되지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

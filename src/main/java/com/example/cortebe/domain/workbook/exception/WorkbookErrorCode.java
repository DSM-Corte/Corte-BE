package com.example.cortebe.domain.workbook.exception;

import com.example.cortebe.global.error.exception.ErrorProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum WorkbookErrorCode implements ErrorProperty {
    WORKBOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "W001", "문제집을 찾을 수 없습니다."),
    DUPLICATE_WORKBOOK_PROBLEM(HttpStatus.CONFLICT, "W002", "이미 문제집에 포함된 문제입니다."),
    WORKBOOK_PROBLEM_NOT_FOUND(HttpStatus.NOT_FOUND, "W003", "문제집에 포함된 문제를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
